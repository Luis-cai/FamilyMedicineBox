package com.family.medbox

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.family.medbox.data.backup.BackupManager
import com.family.medbox.data.db.MediBoxDbHelper
import com.family.medbox.data.prefs.EmergencyStore
import com.family.medbox.data.prefs.FollowUpStore
import com.family.medbox.data.prefs.HealthLogStore
import com.family.medbox.data.prefs.MedicationPlanStore
import com.family.medbox.data.prefs.OpenedStore
import com.family.medbox.data.prefs.PackingStore
import com.family.medbox.data.prefs.ShoppingStore
import com.family.medbox.data.prefs.UiSettingsStore
import com.family.medbox.data.repo.MediBoxRepository
import com.family.medbox.data.sync.PlanSyncManager
import com.family.medbox.data.sync.RemoteNotificationPoller
import com.family.medbox.data.sync.SyncStore
import com.family.medbox.domain.AgentBrain
import com.family.medbox.domain.DrugKnowledge
import com.family.medbox.domain.InsightEngine
import com.family.medbox.domain.ShoppingGuard
import com.family.medbox.domain.ocr.MedicineOcr
import com.family.medbox.domain.ocr.MedicineTextParser
import com.family.medbox.domain.voice.VoiceInput
import com.family.medbox.notify.DoseReminderScheduler
import com.family.medbox.notify.NotificationHelper
import com.family.medbox.notify.ReminderScheduler
import com.family.medbox.notify.RemoteNotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 轻量级依赖容器，避免为演示项目引入额外框架 */
class AppContainer(context: Context) {
    private val dbHelper = MediBoxDbHelper(context)
    val knowledge = DrugKnowledge(context)
    val repository = MediBoxRepository(dbHelper)
    val planStore = MedicationPlanStore(context)
    val packingStore = PackingStore(context)
    val emergencyStore = EmergencyStore(context)
    val healthLogStore = HealthLogStore(context)
    val followUpStore = FollowUpStore(context)
    val openedStore = OpenedStore(context)
    val shoppingStore = ShoppingStore(context)
    val backup = BackupManager(dbHelper, planStore, shoppingStore)
    val uiSettings = UiSettingsStore(context)
    val syncStore = SyncStore(context)
    val syncManager = PlanSyncManager(planStore, syncStore)
    val remotePoller = RemoteNotificationPoller(context, syncStore, planStore)
    val insightEngine = InsightEngine(knowledge)
    val shoppingGuard = ShoppingGuard(knowledge)
    val agentBrain = AgentBrain(knowledge, shoppingGuard, insightEngine)
    val textParser = MedicineTextParser(knowledge)
    val ocr = MedicineOcr()
    val voiceInput = VoiceInput(context)
}

class MediBoxApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannel(this)
        ReminderScheduler.scheduleDailyCheck(this)
        val session = container.syncStore.info.value
        if (session.enabled && session.loggedIn && session.inFamily) {
            RemoteNotificationScheduler.start(this)
        } else {
            RemoteNotificationScheduler.stop(this)
        }
        if (container.planStore.remindersEnabled.value) {
            DoseReminderScheduler.rescheduleAll(this, container.planStore.plans.value)
        }
        registerNetworkWatcher()
    }

    /** 网络恢复时立刻补拉一次，不必等下一次闹钟。 */
    private fun registerNetworkWatcher() {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            manager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val session = container.syncStore.info.value
                        if (!session.enabled || !session.loggedIn || !session.inFamily) return
                        appScope.launch {
                            val result = container.remotePoller.pull()
                            RemoteNotificationScheduler.afterPoll(this@MediBoxApp, result)
                        }
                    }
                }
            )
        }
    }
}
