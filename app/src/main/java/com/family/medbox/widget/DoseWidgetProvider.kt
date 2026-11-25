package com.family.medbox.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.family.medbox.MainActivity
import com.family.medbox.R
import com.family.medbox.data.model.DoseTracker
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.prefs.MedicationPlanStore
import com.family.medbox.util.DateUtils
import java.util.concurrent.Executors

/**
 * 桌面小组件：今日服药进度 + 下一次服药。
 * 整卡点击直接进入用药提醒页打卡；App 内计划或打卡变化时由 [refresh] 主动刷新。
 *
 * 所有 [AppWidgetManager] 调用都放到单独线程：主题切换时桌面会重建，
 * 这时从主线程调 updateAppWidget 会被桌面阻塞，超过 5 秒就会触发 ANR。
 */
class DoseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val app = context.applicationContext
        val ids = appWidgetIds.copyOf()
        worker.execute {
            runCatching {
                val manager = AppWidgetManager.getInstance(app) ?: return@runCatching
                manager.updateAppWidget(ids, buildViews(app))
            }
        }
    }

    companion object {

        /** 串行执行，避免连续刷新时多次跨进程更新。 */
        private val worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "dose-widget").apply { isDaemon = true }
        }

        /** 用内存里的计划与打卡直接刷新，不读磁盘、不占用主线程。 */
        fun refresh(
            context: Context,
            plans: List<MedicationPlan>,
            checks: Map<String, Set<String>>
        ) {
            val app = context.applicationContext
            worker.execute {
                runCatching {
                    val manager = AppWidgetManager.getInstance(app) ?: return@runCatching
                    val ids = manager.getAppWidgetIds(
                        ComponentName(app, DoseWidgetProvider::class.java)
                    )
                    if (ids.isEmpty()) return@runCatching
                    manager.updateAppWidget(ids, buildViews(app, plans, checks))
                }
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val store = MedicationPlanStore(context)
            return buildViews(context, store.plans.value, store.checks.value)
        }

        private fun buildViews(
            context: Context,
            plans: List<MedicationPlan>,
            checks: Map<String, Set<String>>
        ): RemoteViews {
            val today = DateUtils.today()
            val checked = checks[DateUtils.toIso(today)].orEmpty()

            // 今天所有时间点按时间排序，「下一次」取第一个还没打卡的
            val slots = plans
                .filter { it.activeOn(today) }
                .flatMap { plan -> plan.times.map { plan to it } }
                .sortedBy { it.second }
            val total = slots.size
            val done = slots.count { (plan, time) -> plan.checkKey(time) in checked }
            val next = slots.firstOrNull { (plan, time) -> plan.checkKey(time) !in checked }

            val head = when {
                total == 0 -> "今天还没有服药计划"
                next != null -> "${next.second} ${next.first.medicineName}"
                else -> "今天的药都吃完啦"
            }
            val sub = when {
                total == 0 -> "打开 App 添加计划和提醒"
                next != null -> {
                    val plan = next.first
                    if (plan.memberName.isBlank()) plan.dose else "${plan.memberName} · ${plan.dose}"
                }
                else -> "共 $total 次 · 继续保持"
            }

            val views = RemoteViews(context.packageName, R.layout.widget_dose)
            views.setTextViewText(R.id.widget_count, "$done/$total 次")
            views.setTextViewText(R.id.widget_next, head)
            views.setTextViewText(R.id.widget_sub, sub)
            views.setProgressBar(
                R.id.widget_progress,
                100,
                DoseTracker.percent(done, total),
                false
            )

            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_REMINDERS, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }
}
