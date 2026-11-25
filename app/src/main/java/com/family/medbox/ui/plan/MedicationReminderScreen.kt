﻿﻿﻿package com.family.medbox.ui.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.medbox.data.model.DoseTracker
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.ui.components.ChoicePill
import com.family.medbox.ui.components.Pill
import com.family.medbox.ui.components.PrimaryButton
import com.family.medbox.ui.components.SectionHeader
import com.family.medbox.ui.components.SoftCard
import com.family.medbox.ui.theme.Background
import com.family.medbox.ui.theme.Danger
import com.family.medbox.ui.theme.DangerContainer
import com.family.medbox.ui.theme.Divider
import com.family.medbox.ui.theme.Ink
import com.family.medbox.ui.theme.InkMuted
import com.family.medbox.ui.theme.InkSecondary
import com.family.medbox.ui.theme.OnPrimary
import com.family.medbox.ui.theme.Primary
import com.family.medbox.ui.theme.PrimaryContainer
import com.family.medbox.ui.theme.SurfaceWhite
import com.family.medbox.util.DateUtils
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private data class DoseItem(val plan: MedicationPlan, val time: String) {
    fun key(): String = plan.checkKey(time)
}

/** 一条用药记录：某天的某个时间点是否已经打卡 */
private data class RecordDose(
    val plan: MedicationPlan,
    val time: String,
    val taken: Boolean
)

/** 按天分组的用药记录 */
private data class DayRecord(
    val date: LocalDate,
    val items: List<RecordDose>
)

/**
 * 用药提醒：把「今天几点吃什么、吃没吃、这周坚持得怎么样」放在一屏。
 * 提醒开关只影响本地闹钟，历史打卡记录保留。
 */
@Composable
fun MedicationReminderScreen(
    plans: List<MedicationPlan>,
    todayChecked: Set<String>,
    checks: Map<String, Set<String>>,
    remindersEnabled: Boolean,
    /** 从通知进来时要重点标注的那一次服药（planId@time） */
    focusKey: String? = null,
    onToggleReminder: (Boolean) -> Unit,
    onToggleCheck: (MedicationPlan, String) -> Unit,
    onAddPlan: () -> Unit,
    onDeletePlan: (MedicationPlan) -> Unit,
    onClose: () -> Unit
) {
    val today = DateUtils.today()
    val nowLabel = String.format("%02d:%02d", LocalTime.now().hour, LocalTime.now().minute)
    val doses = plans
        .filter { it.activeOn(today) }
        .flatMap { plan -> plan.times.sorted().map { time -> DoseItem(plan, time) } }
        .sortedBy { it.time }

    val (todayDone, todayTotal) = DoseTracker.dailyProgress(plans, checks, today)
    val (weekDone, weekTotal) = DoseTracker.weekProgress(plans, checks)
    val weekRate = DoseTracker.percent(weekDone, weekTotal)
    val weekStats = remember(plans, checks) { weekStats(plans, checks) }
    val todayPercent = DoseTracker.percent(todayDone, todayTotal)
    val pending = doses.count { it.key() !in todayChecked && it.time > nowLabel }
    val overdue = doses.count { it.key() !in todayChecked && it.time <= nowLabel }
    val nextDose = doses.firstOrNull { it.key() !in todayChecked && it.time > nowLabel }

    var historyDays by rememberSaveable { mutableIntStateOf(7) }
    var showHistory by rememberSaveable { mutableStateOf(false) }

    // 从通知进来：把对应的时间点高亮 2.6 秒，让人一眼看到该点哪里
    var highlightKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusKey) {
        if (focusKey == null) return@LaunchedEffect
        highlightKey = focusKey
        delay(2600)
        highlightKey = null
    }
    val history = remember(plans, checks, historyDays) {
        buildList {
            for (offset in 0 until historyDays) {
                val date = today.minusDays(offset.toLong())
                val checked = checks[DateUtils.toIso(date)].orEmpty()
                val items = plans
                    .filter { it.activeOn(date) }
                    .flatMap { plan ->
                        plan.times.sorted().map { time ->
                            RecordDose(
                                plan = plan,
                                time = time,
                                taken = plan.checkKey(time) in checked
                            )
                        }
                    }
                    .filterNot { date == today && !it.taken && it.time > nowLabel }
                    .sortedBy { it.time }
                if (items.isNotEmpty()) add(DayRecord(date = date, items = items))
            }
        }
    }
    val historyTaken = history.sumOf { day -> day.items.count { it.taken } }
    val historyTotal = history.sumOf { day -> day.items.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        Surface(color = SurfaceWhite) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Ink)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "用药提醒",
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink
                        )
                        Text(
                            text = "本地闹钟 · 到点提醒，不上传任何数据",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                }
                HorizontalDivider(color = Divider)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            SoftCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TodayRing(percent = todayPercent)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (todayTotal == 0) "今天没有服药安排" else "今日已服 $todayDone / $todayTotal 次",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = when {
                                !remindersEnabled -> "提醒已关闭，记得手动查看时间表"
                                nextDose != null -> "下一次提醒 ${nextDose.time} · ${nextDose.plan.medicineName}"
                                todayTotal > 0 && todayDone == todayTotal -> "今天的药都吃完了，做得不错"
                                else -> "本周完成率 $weekRate%，按计划继续"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (pending > 0) {
                                Pill(
                                    text = "待服 $pending",
                                    container = Background,
                                    content = InkSecondary
                                )
                            }
                            if (overdue > 0) {
                                Pill(
                                    text = "已过时 $overdue",
                                    container = DangerContainer,
                                    content = Danger
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionHeader(
                title = "今日时间轴",
                trailing = "＋ 加计划",
                onTrailingClick = onAddPlan
            )
            Spacer(Modifier.height(8.dp))

            if (doses.isEmpty()) {
                SoftCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                    Text(
                        text = "今天还没有服药计划",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "添加后会在设定的时间弹出本地提醒。",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "添加服药计划",
                        onClick = onAddPlan,
                        compact = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                doses.forEach { item ->
                    val checked = item.key() in todayChecked
                    val overdueItem = !checked && item.time <= nowLabel
                    DoseTimelineRow(
                        item = item,
                        checked = checked,
                        overdue = overdueItem,
                        highlight = item.key() == highlightKey
                    ) { onToggleCheck(item.plan, item.time) }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionHeader(
                title = "近 7 天依从性",
                trailing = if (weekTotal > 0) "$weekDone / $weekTotal 次" else null
            )
            Spacer(Modifier.height(8.dp))
            SoftCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (weekTotal > 0) "$weekRate%" else "—",
                        style = MaterialTheme.typography.titleLarge,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "依从率",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "漏服 ${weekStats.missed} 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (weekStats.missed > 0) Danger else InkMuted
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "连续 ${weekStats.streak} 天",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }
                Spacer(Modifier.height(12.dp))
                WeekBars(plans = plans, checks = checks)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Divider)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "坚持度",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMuted
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = when {
                            weekTotal == 0 -> "暂无记录"
                            weekRate >= 90 -> "很稳定"
                            weekRate >= 60 -> "还不错"
                            else -> "可以从今天补上"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (weekRate >= 60) Primary else Danger
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 14.dp,
                onClick = { showHistory = !showHistory }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        tint = InkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "用药记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink
                        )
                        Text(
                            text = if (historyTotal > 0) {
                                "已服 $historyTaken / $historyTotal 次 · 点开按天查看"
                            } else {
                                "打卡后按天记录在这里"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                    Icon(
                        if (showHistory) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (showHistory) "收起" else "展开",
                        tint = InkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (showHistory) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 14, 30).forEach { days ->
                        ChoicePill(
                            label = "近 $days 天",
                            selected = historyDays == days,
                            onClick = { historyDays = days }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (history.isEmpty()) {
                    SoftCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                        Text(
                            text = "这段时间还没有服药安排",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "添加计划并打卡后，会按天记录在这里。",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                } else {
                    history.forEach { record ->
                        DayRecordCard(record = record, today = today)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionHeader(title = "提醒设置")
            Spacer(Modifier.height(8.dp))
            SoftCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (remindersEnabled) PrimaryContainer else Background),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (remindersEnabled) Icons.Rounded.NotificationsActive
                            else Icons.Rounded.NotificationsOff,
                            contentDescription = null,
                            tint = if (remindersEnabled) Primary else InkMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "到点提醒",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink
                        )
                        Text(
                            text = if (remindersEnabled) "已开启 · 漏服 30 分钟再提醒"
                            else "已关闭 · 不会响铃，打卡照常记录",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (remindersEnabled) InkMuted else Danger
                        )
                    }
                    Switch(
                        checked = remindersEnabled,
                        onCheckedChange = onToggleReminder,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnPrimary,
                            checkedTrackColor = Primary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Divider,
                            uncheckedBorderColor = Divider
                        )
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Background)
                        .padding(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = InkMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "提醒只在本机触发，疗程结束自动停止。",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionHeader(title = "我的计划", trailing = "${plans.size} 个")
            Spacer(Modifier.height(8.dp))
            if (plans.isEmpty()) {
                Text(
                    text = "还没有长期服药计划，点上方「＋ 加计划」创建。",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted
                )
            } else {
                plans.forEach { plan ->
                    PlanManageRow(plan = plan, onDelete = { onDeletePlan(plan) })
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

private data class WeekStats(val missed: Int, val streak: Int)

/** 近 7 天的漏服次数与连续达标天数 */
private fun weekStats(plans: List<MedicationPlan>, checks: Map<String, Set<String>>): WeekStats {
    val today = DateUtils.today()
    var missed = 0
    var streak = 0
    var streakOpen = true

    for (offset in 0..6) {
        val (dayDone, dayTotal) = DoseTracker.dailyProgress(
            plans = plans,
            checks = checks,
            date = today.minusDays(offset.toLong()),
            skipFuture = offset == 0
        )
        missed += dayTotal - dayDone
        if (dayTotal == 0) continue
        if (dayDone == dayTotal) {
            if (streakOpen) streak += 1
        } else if (offset != 0) {
            // 今天还没到点，不算「断档」，只从昨天开始判断连续达标。
            streakOpen = false
        }
    }
    return WeekStats(missed = missed, streak = streak)
}

@Composable
private fun TodayRing(percent: Int) {
    Box(modifier = Modifier.size(68.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = Divider,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Primary,
                startAngle = -90f,
                sweepAngle = 360f * percent / 100f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.titleMedium,
            color = Primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DoseTimelineRow(
    item: DoseItem,
    checked: Boolean,
    overdue: Boolean,
    highlight: Boolean = false,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (highlight) PrimaryContainer else SurfaceWhite,
        border = BorderStroke(
            if (highlight) 1.5.dp else 1.dp,
            if (highlight) Primary else Divider
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(50.dp)) {
                Text(
                    text = item.time,
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        checked -> InkMuted
                        overdue -> Danger
                        else -> Ink
                    }
                )
                Text(
                    text = when {
                        checked -> "已服"
                        overdue -> "已过时"
                        else -> "待服"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        checked -> Primary
                        overdue -> Danger
                        else -> InkMuted
                    }
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(Divider)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.plan.medicineName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOf(item.plan.dose, item.plan.memberName.ifBlank { "全家" })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (checked) Primary else SurfaceWhite)
                    .border(1.dp, if (checked) Primary else Primary.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (checked) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = OnPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    text = if (checked) "已服" else "打卡",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (checked) OnPrimary else Primary
                )
            }
        }
    }
}

@Composable
private fun WeekBars(
    plans: List<MedicationPlan>,
    checks: Map<String, Set<String>>
) {
    val today = DateUtils.today()
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { date ->
            val (done, total) = DoseTracker.dailyProgress(
                plans = plans,
                checks = checks,
                date = date,
                skipFuture = date == today
            )
            val ratio = if (total == 0) 0f else done.toFloat() / total
            val isToday = date == today
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            // 保底 8dp，避免当天还没打卡时柱子塌成一条细线
                            .height((8 + 48 * ratio).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    ratio <= 0f -> Primary.copy(alpha = 0.16f)
                                    isToday -> Primary
                                    ratio >= 1f -> Primary.copy(alpha = 0.55f)
                                    else -> Primary.copy(alpha = 0.28f)
                                }
                            )
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = weekdays[date.dayOfWeek.value - 1],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) Primary else InkMuted
                )
            }
        }
    }
}

@Composable
private fun DayRecordCard(record: DayRecord, today: LocalDate) {
    val done = record.items.count { it.taken }
    val total = record.items.size
    val allDone = done == total

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, Divider)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayLabel(record.date, today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = weekdayLabel(record.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
                Spacer(Modifier.weight(1f))
                if (allDone) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    text = "$done / $total",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (allDone) Primary else Danger
                )
            }
            record.items.forEach { item ->
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = Divider.copy(alpha = 0.7f)
                )
                RecordRow(item = item, isToday = record.date == today)
            }
        }
    }
}

@Composable
private fun RecordRow(item: RecordDose, isToday: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (item.taken) PrimaryContainer else Background)
                .border(1.dp, if (item.taken) PrimaryContainer else Divider, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (item.taken) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(12.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(InkMuted.copy(alpha = 0.45f))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = item.time,
            style = MaterialTheme.typography.labelMedium,
            color = if (item.taken) InkSecondary else InkMuted,
            modifier = Modifier.width(42.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.plan.medicineName,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.taken) Ink else InkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = "${item.plan.dose} · ${item.plan.memberName.ifBlank { "全家" }}",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                item.taken -> "已服"
                isToday -> "待服"
                else -> "漏服"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
                item.taken -> Primary
                isToday -> InkMuted
                else -> Danger
            }
        )
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String =
    when (today.toEpochDay() - date.toEpochDay()) {
        0L -> "今天"
        1L -> "昨天"
        else -> DateUtils.monthDay(DateUtils.toIso(date))
    }

private fun weekdayLabel(date: LocalDate): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[date.dayOfWeek.value - 1]

@Composable
private fun PlanManageRow(plan: MedicationPlan, onDelete: () -> Unit) {
    val start = DateUtils.parseIso(plan.startDate)
    val today = DateUtils.today()
    val dayIndex = if (start == null) 1 else {
        (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceIn(1, plan.days)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, Divider)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plan.medicineName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    Pill(
                        text = "第 $dayIndex/${plan.days} 天",
                        container = PrimaryContainer,
                        content = Primary
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${plan.timeLabel} · ${plan.dose} · ${plan.memberName.ifBlank { "全家" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "删除计划",
                    tint = InkMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
