package com.family.medbox.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.medbox.AppContainer
import com.family.medbox.data.backup.BackupManager
import com.family.medbox.data.model.DoseTracker
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.Member
import com.family.medbox.data.model.StockStatus
import com.family.medbox.domain.MonthlyReportBuilder
import com.family.medbox.data.prefs.AgentMemoryStore
import com.family.medbox.data.prefs.OpenedShelfLife
import com.family.medbox.domain.AgentBrain
import com.family.medbox.domain.StarterDrug
import com.family.medbox.notify.DoseReminderScheduler
import com.family.medbox.notify.RemoteNotificationScheduler
import com.family.medbox.ui.add.AddMedicineScreen
import com.family.medbox.ui.agent.AgentScreen
import com.family.medbox.ui.agent.ChatMessage
import com.family.medbox.ui.check.CheckScreen
import com.family.medbox.ui.components.ExpiryBadge
import com.family.medbox.ui.components.MemberAvatar
import com.family.medbox.ui.components.Pill
import com.family.medbox.ui.components.PrimaryButton
import com.family.medbox.ui.components.StarterKitSheet
import com.family.medbox.ui.family.EmergencyCardScreen
import com.family.medbox.ui.family.FamilyScreen
import com.family.medbox.ui.family.HealthJournalScreen
import com.family.medbox.ui.family.MemberCareScreen
import com.family.medbox.ui.follow.FollowUpScreen
import com.family.medbox.ui.guide.FoodGuideScreen
import com.family.medbox.ui.home.AllMedicinesScreen
import com.family.medbox.ui.home.HomeScreen
import com.family.medbox.ui.home.OpenedShelfScreen
import com.family.medbox.ui.home.StockFilter
import com.family.medbox.ui.plan.DosePackingScreen
import com.family.medbox.ui.plan.DosePlanSheet
import com.family.medbox.ui.plan.ElderDoseScreen
import com.family.medbox.ui.plan.MedicationReminderScreen
import com.family.medbox.ui.report.MonthlyReportScreen
import com.family.medbox.ui.sync.SyncSheet
import com.family.medbox.ui.theme.Accent
import com.family.medbox.ui.theme.Background
import com.family.medbox.ui.theme.Danger
import com.family.medbox.ui.theme.DangerContainer
import com.family.medbox.ui.theme.Divider
import com.family.medbox.ui.theme.Ink
import com.family.medbox.ui.theme.InkMuted
import com.family.medbox.ui.theme.InkSecondary
import com.family.medbox.ui.theme.Outline
import com.family.medbox.ui.theme.OnPrimary
import com.family.medbox.ui.theme.Primary
import com.family.medbox.ui.theme.PrimaryContainer
import com.family.medbox.ui.theme.SurfaceWhite
import com.family.medbox.util.DateUtils
import com.family.medbox.widget.DoseWidgetProvider
import kotlinx.coroutines.launch
import java.util.UUID

private data class TabItem(val label: String, val icon: ImageVector)

/** 底部导航里「家庭」的位置，用于切页时补拉家庭动态。 */
private const val FAMILY_TAB_INDEX = 3

private val tabs = listOf(
    TabItem("药箱", Icons.Rounded.LocalPharmacy),
    TabItem("问药", Icons.Rounded.ChatBubble),
    TabItem("查重", Icons.Rounded.QrCodeScanner),
    TabItem("家庭", Icons.Rounded.FamilyRestroom)
)

@Composable
fun MediBoxRoot(
    container: AppContainer,
    openRemindersRequest: Int = 0,
    openElderRequest: Int = 0,
    focusDoseKey: String? = null,
    focusDoseRequest: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val medicines by container.repository.medicines.collectAsStateWithLifecycle()
    val members by container.repository.members.collectAsStateWithLifecycle()
    val plans by container.planStore.plans.collectAsStateWithLifecycle()
    val checks by container.planStore.checks.collectAsStateWithLifecycle()
    val planChanges by container.planStore.changes.collectAsStateWithLifecycle()
    val shoppingItems by container.shoppingStore.items.collectAsStateWithLifecycle()
    val remindersEnabled by container.planStore.remindersEnabled.collectAsStateWithLifecycle()
    val packedDays by container.packingStore.packedDays.collectAsStateWithLifecycle()
    val emergencyInfos by container.emergencyStore.infos.collectAsStateWithLifecycle()
    val healthRecords by container.healthLogStore.records.collectAsStateWithLifecycle()
    val followUps by container.followUpStore.items.collectAsStateWithLifecycle()
    val openedMedicines by container.openedStore.opened.collectAsStateWithLifecycle()
    val elderMode by container.uiSettings.elderMode.collectAsStateWithLifecycle()
    val elderFontScale by container.uiSettings.elderFontScale.collectAsStateWithLifecycle()
    val highContrast by container.uiSettings.highContrast.collectAsStateWithLifecycle()
    val syncInfo by container.syncManager.info.collectAsStateWithLifecycle()
    val syncBusy by container.syncManager.busy.collectAsStateWithLifecycle()
    val syncMessage by container.syncManager.message.collectAsStateWithLifecycle()
    val syncError by container.syncManager.error.collectAsStateWithLifecycle()
    val familyEvents by container.syncManager.events.collectAsStateWithLifecycle()
    val insights = remember(medicines) { container.insightEngine.build(medicines) }
    val report = remember(medicines) { MonthlyReportBuilder.build(medicines) }
    val todayKey = DateUtils.toIso(DateUtils.today())
    val todayChecked = checks[todayKey].orEmpty()
    val weekProgress = remember(plans, checks) { DoseTracker.weekProgress(plans, checks) }
    val weekDone = weekProgress.first
    val weekTotal = weekProgress.second
    val ownedIngredients = remember(medicines) {
        medicines.map { it.ingredient }.filter { it.isNotBlank() }.toSet()
    }

    // 桌面小组件跟随计划与打卡实时刷新（内部走后台线程，避免主题切换时卡住主线程）
    LaunchedEffect(plans, checks) {
        DoseWidgetProvider.refresh(context, plans, checks)
    }

    var tabIndex by rememberSaveable { mutableStateOf(0) }
    // 记录上一个 Tab，系统返回键优先回到这里
    var lastTabIndex by rememberSaveable { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Medicine?>(null) }
    var detail by remember { mutableStateOf<Medicine?>(null) }
    var showReport by remember { mutableStateOf(false) }
    var showReminders by remember { mutableStateOf(false) }
    var showPacking by remember { mutableStateOf(false) }
    var showEmergency by remember { mutableStateOf(false) }
    var showJournal by remember { mutableStateOf(false) }
    var showFollowUp by remember { mutableStateOf(false) }
    var showFoodGuide by remember { mutableStateOf(false) }
    var showOpenedShelf by remember { mutableStateOf(false) }
    // 点开某位成员后显示的照护建议页
    var careMember by remember { mutableStateOf<Member?>(null) }
    var showElder by remember { mutableStateOf(false) }
    var showStarterKit by remember { mutableStateOf(false) }
    var showPlanSheet by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var showMedicines by remember { mutableStateOf(false) }
    var medicineFilter by remember { mutableStateOf(StockFilter.ALL) }
    var planSeed by remember { mutableStateOf<String?>(null) }
    var planSeedDose by remember { mutableStateOf("") }
    var planSeedTimes by remember { mutableStateOf<List<String>>(emptyList()) }
    var planSeedDays by remember { mutableStateOf(7) }
    var agentMemory by remember { mutableStateOf(AgentBrain.Memory()) }
    val agentMemoryStore = remember { AgentMemoryStore(context) }
    val agentLastQuestion = remember { agentMemoryStore.load().lastQuestion }
    val messages = remember { mutableStateListOf<ChatMessage>() }

    // 备份导出与恢复：文件位置由系统文件选择器决定，App 自己不上传
    var pendingRestore by remember { mutableStateOf<Pair<String, BackupManager.Preview>?>(null) }
    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val json = container.backup.exportJson()
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                ?: error("无法写入所选文件")
        }.onSuccess {
            Toast.makeText(context, "备份已导出", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show()
        }
    }
    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("无法读取所选文件")
            pendingRestore = raw to container.backup.preview(raw)
        }.onFailure {
            Toast.makeText(context, it.message ?: "读取失败", Toast.LENGTH_LONG).show()
        }
    }

    // 跨会话恢复最近聊过的药，重启后「那它呢」还能接上
    LaunchedEffect(medicines.size) {
        if (agentMemory.medicine == null) {
            val snapshot = agentMemoryStore.load()
            if (snapshot.medicineName.isNotBlank()) {
                agentMemory = AgentBrain.Memory(
                    medicine = medicines.firstOrNull { it.name == snapshot.medicineName },
                    ingredient = snapshot.ingredient.takeIf { it.isNotBlank() },
                    member = members.firstOrNull { it.name == snapshot.memberName }
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        val session = container.syncManager.info.value
        if (session.enabled && session.inFamily) {
            container.syncManager.syncNow()
            container.remotePoller.pull()
            container.syncManager.refreshEvents()
            DoseReminderScheduler.rescheduleAll(context, container.planStore.plans.value)
        }
    }

    // 加入家庭组后立刻补一次动态，并接上实时通道，家人打卡不用等下一次轮询。
    LaunchedEffect(syncInfo.inFamily, syncInfo.familyId) {
        if (syncInfo.enabled && syncInfo.inFamily) {
            container.syncManager.refreshEvents()
            container.syncManager.startEventStream(scope)
        } else {
            container.syncManager.stopEventStream()
        }
    }

    // 切到「家庭」页时再补拉一次，刚发生的家人打卡能马上看到
    LaunchedEffect(tabIndex) {
        if (tabIndex == FAMILY_TAB_INDEX && syncInfo.inFamily) {
            container.syncManager.refreshEvents()
        }
    }

    // 回到前台补拉一次并重新接上 SSE；切到后台主动断开长连接，省电也省服务端资源。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val session = container.syncManager.info.value
                    if (session.enabled && session.loggedIn && session.inFamily) {
                        scope.launch {
                            container.remotePoller.pull()
                            container.syncManager.refreshEvents()
                        }
                        container.syncManager.startEventStream(scope)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> container.syncManager.stopEventStream()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            container.syncManager.stopEventStream()
        }
    }

    LaunchedEffect(openRemindersRequest) {
        if (openRemindersRequest > 0) showReminders = true
    }

    LaunchedEffect(openElderRequest) {
        if (openElderRequest > 0) showElder = true
    }

    // 从服药通知点进来：打开用药提醒页，并高亮通知对应的那一次服药
    var highlightDose by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusDoseRequest) {
        if (focusDoseRequest > 0 && focusDoseKey != null) {
            highlightDose = focusDoseKey
            showReminders = true
        }
    }
    LaunchedEffect(showReminders) {
        // 离开提醒页后清掉，避免下次再打开时重复高亮
        if (!showReminders) highlightDose = null
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun ask(text: String) {
        messages.add(ChatMessage(fromUser = true, text = text))
        val reply = container.agentBrain.answer(
            input = text,
            medicines = medicines,
            members = members,
            plans = plans,
            todayChecked = todayChecked,
            checks = checks,
            shoppingItems = shoppingItems.map { it.name },
            memory = agentMemory
        )
        agentMemory = reply.memory
        agentMemoryStore.save(
            AgentMemoryStore.Snapshot(
                medicineName = reply.memory.medicine?.name.orEmpty(),
                ingredient = reply.memory.ingredient.orEmpty(),
                memberName = reply.memory.member?.name.orEmpty(),
                lastQuestion = text
            )
        )
        messages.add(
            ChatMessage(
                fromUser = false,
                text = reply.text,
                medicines = reply.medicines,
                tone = reply.tone,
                suggestions = reply.suggestions,
                steps = reply.steps,
                actions = reply.actions,
                candidates = reply.candidates
            )
        )
    }

    fun createPlan(draft: AgentBrain.PlanDraft) {
        val member = members.firstOrNull { draft.memberName.isNotBlank() && it.name == draft.memberName }
            ?: members.firstOrNull()
        val plan = MedicationPlan(
            id = UUID.randomUUID().toString(),
            medicineName = draft.medicineName,
            dose = draft.dose,
            memberId = member?.id,
            memberName = member?.name.orEmpty(),
            times = draft.times,
            startDate = todayKey,
            days = draft.days
        )
        container.planStore.addPlan(plan)
        if (remindersEnabled) {
            DoseReminderScheduler.schedulePlan(context, plan)
        }
        scope.launch { container.syncManager.pushPlan(plan) }
    }

    fun runAgentAction(action: AgentBrain.Action) {
        when (action.kind) {
            AgentBrain.Action.Kind.OPEN_MEDICINE ->
                medicines.firstOrNull { it.id == action.medicineId }?.let { detail = it }

            AgentBrain.Action.Kind.OPEN_MEDICINES -> {
                medicineFilter = runCatching { StockFilter.valueOf(action.filter) }
                    .getOrDefault(StockFilter.ALL)
                showMedicines = true
            }

            AgentBrain.Action.Kind.OPEN_REMINDERS -> showReminders = true

            AgentBrain.Action.Kind.ADD_PLAN -> {
                planSeed = action.medicineName.takeIf { it.isNotBlank() }
                showPlanSheet = true
            }

            AgentBrain.Action.Kind.CREATE_PLAN -> action.plan?.let { draft ->
                createPlan(draft)
                messages.add(
                    ChatMessage(
                        fromUser = false,
                        text = "已创建服药计划：${draft.medicineName} ${draft.dose}，" +
                            "${draft.times.joinToString("、")}，共 ${draft.days} 天。到点会在本机提醒你。",
                        tone = AgentBrain.Tone.GOOD,
                        suggestions = listOf("今天还有哪些药没吃"),
                        actions = listOf(
                            AgentBrain.Action("查看提醒", AgentBrain.Action.Kind.OPEN_REMINDERS)
                        )
                    )
                )
            }

            AgentBrain.Action.Kind.EDIT_PLAN -> action.plan?.let { draft ->
                planSeed = draft.medicineName
                planSeedDose = draft.dose
                planSeedTimes = draft.times
                planSeedDays = draft.days
                showPlanSheet = true
            }

            AgentBrain.Action.Kind.ADD_MEDICINE -> adding = true

            AgentBrain.Action.Kind.ADD_TO_SHOPPING -> {
                container.shoppingStore.addAll(action.items)
                messages.add(
                    ChatMessage(
                        fromUser = false,
                        text = "已加入购物清单：${action.items.joinToString("、")}。\n\n" +
                            "买回来后跟我说「买回来了」，我帮你核销。",
                        tone = AgentBrain.Tone.GOOD,
                        suggestions = listOf("买回来了", "查看购物清单"),
                        actions = listOf(
                            AgentBrain.Action("拍照入库", AgentBrain.Action.Kind.ADD_MEDICINE)
                        )
                    )
                )
            }

            AgentBrain.Action.Kind.CLEAR_SHOPPING -> {
                container.shoppingStore.clear()
                messages.add(
                    ChatMessage(
                        fromUser = false,
                        text = "购物清单已清空。记得拍照入库，把新买的药和有效期补进药箱。",
                        tone = AgentBrain.Tone.GOOD,
                        actions = listOf(
                            AgentBrain.Action("拍照入库", AgentBrain.Action.Kind.ADD_MEDICINE)
                        )
                    )
                )
            }
        }
    }

    val planSheet: @Composable () -> Unit = {
        if (showPlanSheet) {
            DosePlanSheet(
                members = members,
                existingPlans = plans,
                medicines = medicines,
                initialMedicineName = planSeed,
                initialDose = planSeedDose,
                initialTimes = planSeedTimes,
                initialDays = planSeedDays,
                onDismiss = {
                    showPlanSheet = false
                    planSeed = null
                    planSeedDose = ""
                    planSeedTimes = emptyList()
                    planSeedDays = 7
                },
                onDelete = { plan ->
                    container.planStore.deletePlan(plan.id)
                    DoseReminderScheduler.cancelPlan(context, plan)
                    scope.launch { container.syncManager.pushDelete(plan.id) }
                },
                onSave = { name, dose, member, times, days ->
                    val plan = MedicationPlan(
                        id = UUID.randomUUID().toString(),
                        medicineName = name,
                        dose = dose,
                        memberId = member?.id,
                        memberName = member?.name.orEmpty(),
                        times = times,
                        startDate = todayKey,
                        days = days
                    )
                    container.planStore.addPlan(plan)
                    if (remindersEnabled) {
                        DoseReminderScheduler.schedulePlan(context, plan)
                    }
                    scope.launch { container.syncManager.pushPlan(plan) }
                    showPlanSheet = false
                }
            )
        }
    }

    if (showReport) {
        BackHandler { showReport = false }
        MonthlyReportScreen(report = report, onClose = { showReport = false })
        return
    }

    // 打卡统一入口：勾选时按剂量扣减药箱库存，取消勾选时补回
    val toggleDose: (MedicationPlan, String) -> Unit = { plan, time ->
        container.planStore.toggleCheck(todayKey, plan.id, time)
        val taken = container.planStore.isChecked(todayKey, plan.id, time)
        scope.launch {
            container.syncManager.pushCheck(todayKey, plan.id, time, taken)
            container.syncManager.broadcastDose(plan, time, taken)
            adjustStockForDose(container, medicines, plan, taken)
        }
    }

    if (showElder) {
        BackHandler { showElder = false }
        ElderDoseScreen(
            plans = plans,
            members = members,
            todayChecked = todayChecked,
            onToggleCheck = toggleDose,
            onClose = { showElder = false }
        )
        return
    }

    if (showReminders) {
        BackHandler { showReminders = false }
        MedicationReminderScreen(
            plans = plans,
            todayChecked = todayChecked,
            checks = checks,
            remindersEnabled = remindersEnabled,
            focusKey = highlightDose,
            onToggleReminder = { enabled ->
                container.planStore.setRemindersEnabled(enabled)
                if (enabled) {
                    DoseReminderScheduler.rescheduleAll(context, plans)
                } else {
                    plans.forEach { DoseReminderScheduler.cancelPlan(context, it) }
                }
            },
            onToggleCheck = toggleDose,
            onAddPlan = { showPlanSheet = true },
            onDeletePlan = { plan ->
                container.planStore.deletePlan(plan.id)
                DoseReminderScheduler.cancelPlan(context, plan)
                scope.launch { container.syncManager.pushDelete(plan.id) }
            },
            onClose = { showReminders = false }
        )
        planSheet()
        return
    }

    if (showPacking) {
        BackHandler { showPacking = false }
        DosePackingScreen(
            plans = plans,
            members = members,
            packedDays = packedDays,
            onTogglePacked = { container.packingStore.toggle(it) },
            onClose = { showPacking = false }
        )
        return
    }

    if (showEmergency) {
        BackHandler { showEmergency = false }
        EmergencyCardScreen(
            members = members,
            plans = plans,
            infos = emergencyInfos,
            onSave = { memberId, info -> container.emergencyStore.save(memberId, info) },
            onClose = { showEmergency = false }
        )
        return
    }

    if (showJournal) {
        BackHandler { showJournal = false }
        HealthJournalScreen(
            members = members,
            records = healthRecords,
            plans = plans,
            onAdd = { container.healthLogStore.add(it) },
            onDelete = { container.healthLogStore.delete(it) },
            onClose = { showJournal = false }
        )
        return
    }

    if (showFollowUp) {
        BackHandler { showFollowUp = false }
        FollowUpScreen(
            members = members,
            items = followUps,
            onSave = { container.followUpStore.save(it) },
            onDelete = { container.followUpStore.delete(it) },
            onClose = { showFollowUp = false }
        )
        return
    }

    if (showFoodGuide) {
        BackHandler { showFoodGuide = false }
        FoodGuideScreen(
            medicines = medicines,
            onClose = { showFoodGuide = false }
        )
        return
    }

    if (showOpenedShelf) {
        BackHandler { showOpenedShelf = false }
        OpenedShelfScreen(
            medicines = medicines,
            opened = openedMedicines,
            onMarkOpened = { container.openedStore.markOpened(it.id) },
            onClear = { container.openedStore.clear(it.id) },
            onClose = { showOpenedShelf = false }
        )
        return
    }

    val careTarget = careMember
    if (careTarget != null) {
        BackHandler { careMember = null }
        MemberCareScreen(
            member = careTarget,
            medicines = medicines,
            plans = plans,
            checks = checks,
            opened = openedMedicines,
            followUps = followUps,
            knowledge = container.knowledge,
            onClose = { careMember = null }
        )
        return
    }

    if (showStarterKit) {
        StarterKitSheet(
            ownedIngredients = ownedIngredients,
            onDismiss = { showStarterKit = false },
            onAdd = { drugs ->
                scope.launch {
                    container.repository.addMedicines(drugs.map { it.toMedicineEntry() })
                    showStarterKit = false
                }
            }
        )
    }

    if (adding || editing != null) {
        BackHandler {
            adding = false
            editing = null
        }
        AddMedicineScreen(
            container = container,
            members = members,
            editing = editing,
            onClose = {
                adding = false
                editing = null
            },
            onOpenStarterKit = { showStarterKit = true },
            onSave = { medicine ->
                scope.launch {
                    if (medicine.id == 0L) {
                        container.repository.addMedicine(medicine)
                    } else {
                        container.repository.updateMedicine(medicine)
                    }
                    adding = false
                    editing = null
                }
            }
        )
        return
    }

    BackHandler(enabled = showMedicines) { showMedicines = false }

    // 在非「药箱」Tab 按返回，先回到上一个 Tab，而不是直接退出应用
    BackHandler(enabled = tabIndex != 0) {
        tabIndex = lastTabIndex
        lastTabIndex = 0
    }

    Scaffold(
        containerColor = Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            BottomNav(
                selected = tabIndex,
                onSelect = { index ->
                    // 已在「药箱」时再点一次，从全部药品清单回到首页
                    if (index == 0 && tabIndex == 0) showMedicines = false
                    if (index != tabIndex) {
                        lastTabIndex = tabIndex
                        tabIndex = index
                    }
                }
            )
        },
        floatingActionButton = {
            if (tabIndex == 0) {
                FloatingActionButton(
                    onClick = { adding = true },
                    containerColor = Primary,
                    contentColor = OnPrimary,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "录入药品")
                }
            }
        }
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            when (tabIndex) {
                0 -> if (showMedicines) {
                    AllMedicinesScreen(
                        medicines = medicines,
                        members = members,
                        initialFilter = medicineFilter,
                        onBack = { showMedicines = false },
                        onSelectMedicine = { detail = it }
                    )
                } else HomeScreen(
                    medicines = medicines,
                    members = members,
                    insights = insights,
                    plans = plans,
                    todayChecked = todayChecked,
                    weekDone = weekDone,
                    weekTotal = weekTotal,
                    elderMode = elderMode,
                    onSelectMedicine = { detail = it },
                    onToggleCheck = toggleDose,
                    onAddPlan = { showPlanSheet = true },
                    onOpenReminders = { showReminders = true },
                    onOpenPacking = { showPacking = true },
                    onOpenReport = { showReport = true },
                    onOpenStarterKit = { showStarterKit = true },
                    onOpenMedicines = { filter ->
                        medicineFilter = filter
                        showMedicines = true
                    }
                )

                1 -> AgentScreen(
                    container = container,
                    messages = messages,
                    lastTopic = agentLastQuestion,
                    onAsk = { ask(it) },
                    onOpenMedicine = { detail = it },
                    onAction = { runAgentAction(it) }
                )

                2 -> CheckScreen(
                    container = container,
                    medicines = medicines,
                    members = members,
                    onOpenMedicine = { detail = it },
                    onAddWithName = { name ->
                        editing = Medicine(name = name)
                    },
                    onAskAgent = { text ->
                        lastTabIndex = 2
                        tabIndex = 1
                        ask(text)
                    }
                )

                else -> FamilyScreen(
                    members = members,
                    medicines = medicines,
                    plans = plans,
                    todayChecked = todayChecked,
                    syncInfo = syncInfo,
                    pendingSync = planChanges.size,
                    familyEvents = familyEvents,
                    onAddMember = { name, relation, needsCare ->
                        scope.launch {
                            container.repository.addMember(
                                Member(
                                    name = name,
                                    relation = relation,
                                    colorIndex = members.size % 6,
                                    needsCare = needsCare
                                )
                            )
                        }
                    },
                    onToggleCare = { member ->
                        scope.launch {
                            container.repository.setMemberCare(member, !member.needsCare)
                        }
                    },
                    onDeleteMember = { member ->
                        scope.launch { container.repository.deleteMember(member.id) }
                    },
                    onOpenMember = { careMember = it },
                    onOpenEmergency = { showEmergency = true },
                    onOpenJournal = { showJournal = true },
                    onOpenFollowUp = { showFollowUp = true },
                    onOpenFoodGuide = { showFoodGuide = true },
                    onOpenOpened = { showOpenedShelf = true },
                    onOpenElder = { showElder = true },
                    elderMode = elderMode,
                    onToggleElderMode = { container.uiSettings.setElderMode(!elderMode) },
                    elderFontScale = elderFontScale,
                    onElderFontScaleChange = { container.uiSettings.setElderFontScale(it) },
                    highContrast = highContrast,
                    onToggleHighContrast = { container.uiSettings.setHighContrast(!highContrast) },
                    onOpenSync = { showSync = true },
                    onExportBackup = { exportBackup.launch(BackupManager.suggestedFileName()) },
                    onImportBackup = { importBackup.launch(arrayOf("application/json")) }
                )
            }
        }
    }

    planSheet()

    pendingRestore?.let { (raw, preview) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("用备份覆盖当前数据？") },
            text = {
                Text(
                    "备份包含 ${preview.medicines} 种药品、${preview.members} 位成员、" +
                        "${preview.plans} 个服药计划" +
                        (if (preview.exportedAt.isNotBlank()) "，导出于 ${preview.exportedAt}" else "") +
                        "。恢复后当前药箱会被替换，建议先导出一份现在的数据。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestore = null
                        runCatching { container.backup.restore(raw) }
                            .onSuccess { count ->
                                container.repository.refresh()
                                if (container.planStore.remindersEnabled.value) {
                                    DoseReminderScheduler.rescheduleAll(
                                        context,
                                        container.planStore.plans.value
                                    )
                                }
                                Toast.makeText(context, "已恢复 $count 种药品", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(context, "恢复失败：${it.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                ) { Text("覆盖恢复") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("取消") }
            }
        )
    }

    if (showSync) {
        SyncSheet(
            info = syncInfo,
            pending = planChanges.size,
            busy = syncBusy,
            message = syncMessage,
            messageIsError = syncError,
            onDismiss = { showSync = false },
            onLogin = { server, nickname, phone ->
                scope.launch {
                    container.syncManager.updateServerUrl(server)
                    if (container.syncManager.login(nickname, phone)) {
                        RemoteNotificationScheduler.start(context)
                        container.remotePoller.pull(force = true)
                    }
                }
            },
            onCreateFamily = { name ->
                scope.launch {
                    if (container.syncManager.createFamily(name)) {
                        RemoteNotificationScheduler.start(context)
                        container.remotePoller.pull(force = true)
                    }
                }
            },
            onJoinFamily = { code ->
                scope.launch {
                    if (container.syncManager.joinFamily(code)) {
                        RemoteNotificationScheduler.start(context)
                        container.remotePoller.pull(force = true)
                    }
                }
            },
            onSync = {
                scope.launch {
                    container.syncManager.syncNow()
                    DoseReminderScheduler.rescheduleAll(context, container.planStore.plans.value)
                    container.remotePoller.pull(force = true)
                }
            },
            onToggleEnabled = { enabled ->
                container.syncManager.setEnabled(enabled)
                if (enabled) {
                    RemoteNotificationScheduler.start(context)
                    scope.launch { container.remotePoller.pull(force = true) }
                } else {
                    RemoteNotificationScheduler.stop(context)
                }
            },
            onSignOut = {
                container.syncManager.clearSession()
                RemoteNotificationScheduler.stop(context)
                container.remotePoller.reset()
            }
        )
    }

    detail?.let { medicine ->
        MedicineDetailSheet(
            medicine = medicine,
            member = members.firstOrNull { it.id == medicine.memberId },
            note = container.knowledge.entryOf(medicine.ingredient)?.note,
            plans = plans,
            checks = checks,
            openedIso = openedMedicines[medicine.id],
            onMarkOpened = { container.openedStore.markOpened(medicine.id) },
            onClearOpened = { container.openedStore.clear(medicine.id) },
            onDismiss = { detail = null },
            onEdit = {
                detail = null
                editing = medicine
            },
            onDelete = {
                scope.launch { container.repository.deleteMedicine(medicine.id) }
                detail = null
            },
            onOpenReminders = {
                detail = null
                showReminders = true
            },
            onAddPlan = {
                planSeed = medicine.name
                planSeedDose = ""
                planSeedTimes = emptyList()
                planSeedDays = 7
                detail = null
                showPlanSheet = true
            }
        )
    }
}

private fun StarterDrug.toMedicineEntry(): Medicine = Medicine(
    name = name,
    ingredient = ingredient,
    category = category,
    spec = spec,
    form = form,
    quantity = quantity,
    unit = unit,
    expiryDate = null,
    location = "待整理",
    note = note
)

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = SurfaceWhite,
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, Divider)
        ) {
            Row(
                modifier = Modifier.padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val active = selected == index
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (active) PrimaryContainer else Color.Transparent)
                            .clickable { onSelect(index) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            tint = if (active) Primary else InkMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) Primary else InkMuted,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicineDetailSheet(
    medicine: Medicine,
    member: Member?,
    note: String?,
    plans: List<MedicationPlan>,
    checks: Map<String, Set<String>>,
    openedIso: String?,
    onMarkOpened: () -> Unit,
    onClearOpened: () -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenReminders: () -> Unit,
    onAddPlan: () -> Unit
) {
    var confirming by remember { mutableStateOf(false) }
    val relatedPlans = remember(plans, medicine.name) {
        val name = medicine.name.trim()
        if (name.isEmpty()) emptyList() else plans.filter { it.medicineName.trim() == name }
    }
    val accent = when (medicine.status) {
        StockStatus.EXPIRED -> Danger
        StockStatus.EXPIRING -> Accent
        StockStatus.NORMAL -> Primary
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceWhite,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        medicine.name.take(1),
                        style = MaterialTheme.typography.titleLarge,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medicine.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExpiryBadge(medicine)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = DateUtils.displayCompact(medicine.expiryDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = InkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoCell(
                    label = "主要成分",
                    value = medicine.ingredient.ifBlank { "未记录" },
                    modifier = Modifier.weight(1f)
                )
                InfoCell(
                    label = "规格剂型",
                    value = listOf(medicine.spec, medicine.form)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { "未记录" },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoCell(
                    label = "库存数量",
                    value = medicine.stockLabel,
                    modifier = Modifier.weight(1f)
                )
                InfoCell(
                    label = "存放位置",
                    value = medicine.location.ifBlank { "未记录" },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoCell(
                    label = "归属成员",
                    value = member?.name ?: "未分配",
                    modifier = Modifier.weight(1f)
                )
                InfoCell(
                    label = "有效期",
                    value = DateUtils.displayDate(medicine.expiryDate),
                    modifier = Modifier.weight(1f)
                )
            }

            val shelfDays = OpenedShelfLife.shelfDays(medicine)
            if (shelfDays != null) {
                Spacer(Modifier.height(10.dp))
                val usedDays = openedIso?.let { OpenedShelfLife.usedDays(it) } ?: 0L
                val leftDays = openedIso?.let { OpenedShelfLife.daysLeft(medicine, it) }
                val expired = leftDays != null && leftDays < 0L
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (openedIso == null) PrimaryContainer else Background)
                        .clickable { if (openedIso == null) onMarkOpened() else onClearOpened() }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = if (openedIso == null) Primary else if (expired) Danger else InkSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "开封效期",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                        Text(
                            text = if (openedIso == null) {
                                "开封后可用 $shelfDays 天 · 点此记录开封"
                            } else {
                                val label = when {
                                    leftDays == null -> "已记录"
                                    leftDays < 0L -> "已过期 ${-leftDays} 天"
                                    leftDays == 0L -> "今天到期"
                                    else -> "还剩 $leftDays 天"
                                }
                                "第 ${usedDays + 1} 天 · $label"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink
                        )
                    }
                    Text(
                        text = if (openedIso == null) "今天开封" else "撤销",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (openedIso == null) Primary else InkMuted
                    )
                }
            }

            if (medicine.note.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                InfoCell(label = "备注", value = medicine.note, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(18.dp))
            MedicinePlanSection(
                plans = relatedPlans,
                checks = checks,
                onOpenReminders = onOpenReminders,
                onAddPlan = onAddPlan
            )

            if (!note.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PrimaryContainer)
                        .padding(14.dp)
                ) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = Primary,
                        lineHeight = 19.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = "编辑信息",
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { confirming = true },
                    shape = RoundedCornerShape(12.dp),
                    color = DangerContainer
                ) {
                    Text(
                        text = "删除这盒",
                        modifier = Modifier.padding(vertical = 13.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Danger,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (confirming) {
        Dialog(onDismissRequest = { confirming = false }) {
            Surface(shape = RoundedCornerShape(22.dp), color = SurfaceWhite) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("删除这盒药？", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "「${medicine.name}」将从家庭药箱记忆中移除，此操作不可恢复。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary,
                        lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(
                            onClick = { confirming = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("取消", color = InkMuted)
                        }
                        TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                            Text("确认删除", color = Danger)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 药品详情里的用药提醒：展示同名药的全部计划与最近 7 天完成情况。
 * 点计划行直接去打卡页；还没有计划时给一个就地添加入口。
 */
@Composable
private fun MedicinePlanSection(
    plans: List<MedicationPlan>,
    checks: Map<String, Set<String>>,
    onOpenReminders: () -> Unit,
    onAddPlan: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("用药提醒", style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.weight(1f))
        if (plans.isNotEmpty()) {
            Text(
                text = "去打卡",
                style = MaterialTheme.typography.labelMedium,
                color = Primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenReminders)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
    Spacer(Modifier.height(8.dp))

    if (plans.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Background)
                .clickable(onClick = onAddPlan)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.NotificationsNone,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("还没有用药计划", style = MaterialTheme.typography.bodyMedium, color = Ink)
                Text(
                    text = "设好时间和剂量，到点自动提醒",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
            }
            Text("添加", style = MaterialTheme.typography.labelLarge, color = Primary)
        }
    } else {
        plans.forEach { plan ->
            PlanSummaryRow(plan = plan, checks = checks, onClick = onOpenReminders)
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .border(1.dp, Outline, RoundedCornerShape(50))
                .clickable(onClick = onAddPlan)
                .padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("再建一个计划", style = MaterialTheme.typography.labelMedium, color = Primary)
        }
    }
}

@Composable
private fun PlanSummaryRow(
    plan: MedicationPlan,
    checks: Map<String, Set<String>>,
    onClick: () -> Unit
) {
    val week = remember(plan, checks) { planDoseWeek(plan, checks) }
    val doneDays = week.count { it.total > 0 && it.checked == it.total }
    val dueDays = week.count { it.total > 0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    plan.times.sorted().forEach { time ->
                        Surface(shape = RoundedCornerShape(8.dp), color = PrimaryContainer) {
                            Text(
                                text = time,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = listOf(plan.memberName.ifBlank { "全家" }, plan.dose, planStatusLabel(plan))
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                WeekDots(days = week)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (dueDays == 0) "近 7 天" else "近 7 天 $doneDays/$dueDays",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
            }
        }
    }
}

@Composable
private fun WeekDots(days: List<DoseDay>) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        days.forEach { day ->
            val color = when {
                day.total == 0 -> Divider.copy(alpha = 0.5f)
                day.checked == day.total -> Primary
                day.checked > 0 -> Primary.copy(alpha = 0.4f)
                else -> Divider
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

private data class DoseDay(val checked: Int, val total: Int)

/** 最近 7 天（含今天，旧 → 新）每天应服与已完成次数。 */
private fun planDoseWeek(
    plan: MedicationPlan,
    checks: Map<String, Set<String>>
): List<DoseDay> {
    val today = DateUtils.today()
    val now = java.time.LocalTime.now()
    val nowLabel = String.format("%02d:%02d", now.hour, now.minute)
    return (6 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        if (!plan.activeOn(date)) return@map DoseDay(0, 0)
        val due = plan.times.filter { offset > 0 || it <= nowLabel }
        val checked = checks[DateUtils.toIso(date)].orEmpty()
        DoseDay(due.count { plan.checkKey(it) in checked }, due.size)
    }
}

private fun planStatusLabel(plan: MedicationPlan): String {
    val today = DateUtils.today()
    val start = DateUtils.parseIso(plan.startDate) ?: return ""
    val last = plan.lastDate() ?: return ""
    return when {
        today.isBefore(start) -> "${start.monthValue}月${start.dayOfMonth}日开始"
        today.isAfter(last) -> "已结束"
        else -> "剩 ${java.time.temporal.ChronoUnit.DAYS.between(today, last) + 1} 天"
    }
}

@Composable
private fun InfoCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Background)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/**
 * 打卡联动库存：勾选时按每次剂量扣减，取消勾选时原样补回。
 * 计划只记药名，同名时优先匹配同一位成员的药，避免扣错人。
 */
private suspend fun adjustStockForDose(
    container: AppContainer,
    medicines: List<Medicine>,
    plan: MedicationPlan,
    taken: Boolean
) {
    val name = plan.medicineName.trim()
    if (name.isEmpty()) return
    val candidates = medicines.filter { it.name.trim() == name }
    if (candidates.isEmpty()) return
    val target = candidates.firstOrNull { plan.memberId != null && it.memberId == plan.memberId }
        ?: candidates.first()
    val amount = doseAmount(plan.dose)
    val next = if (taken) (target.quantity - amount).coerceAtLeast(0) else target.quantity + amount
    if (next != target.quantity) {
        container.repository.updateMedicine(target.copy(quantity = next))
    }
}

/** 从「1 片」这类剂量文案里取每次用量，取不到按 1 算 */
private fun doseAmount(dose: String): Int =
    (Regex("\\d+").find(dose)?.value?.toIntOrNull() ?: 1).coerceIn(1, 10)
