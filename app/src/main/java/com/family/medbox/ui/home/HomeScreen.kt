package com.family.medbox.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.model.Member
import com.family.medbox.data.model.StockStatus
import com.family.medbox.domain.AgentAction
import com.family.medbox.domain.Insight
import com.family.medbox.domain.InsightSeverity
import com.family.medbox.domain.ProactiveEngine
import com.family.medbox.domain.ProactiveSuggestion
import com.family.medbox.domain.SceneEventType
import com.family.medbox.domain.SuggestionSeverity
import com.family.medbox.ui.components.Pill
import com.family.medbox.ui.components.ScreenHeader
import com.family.medbox.ui.components.SoftCard
import com.family.medbox.util.DateUtils
import com.family.medbox.ui.theme.Accent
import com.family.medbox.ui.theme.AccentContainer
import com.family.medbox.ui.theme.AccentInk
import com.family.medbox.ui.theme.Background
import com.family.medbox.ui.theme.Danger
import com.family.medbox.ui.theme.DangerContainer
import com.family.medbox.ui.theme.Divider
import com.family.medbox.ui.theme.Ink
import com.family.medbox.ui.theme.HeroTint
import com.family.medbox.ui.theme.InkMuted
import com.family.medbox.ui.theme.InkSecondary
import com.family.medbox.ui.theme.OnPrimary
import com.family.medbox.ui.theme.Outline
import com.family.medbox.ui.theme.Primary
import com.family.medbox.ui.theme.PrimaryContainer
import com.family.medbox.ui.theme.SurfaceWhite
import kotlinx.coroutines.delay
import java.time.LocalDateTime

@Composable
fun HomeScreen(
    medicines: List<Medicine>,
    members: List<Member>,
    insights: List<Insight>,
    plans: List<MedicationPlan>,
    todayChecked: Set<String>,
    weekDone: Int,
    weekTotal: Int,
    elderMode: Boolean = false,
    onSelectMedicine: (Medicine) -> Unit,
    onToggleCheck: (MedicationPlan, String) -> Unit,
    onAddPlan: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenPacking: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenStarterKit: () -> Unit,
    onOpenMedicines: (StockFilter) -> Unit
) {
    val expiringCount = medicines.count { it.status == StockStatus.EXPIRING }
    val expiredCount = medicines.count { it.status == StockStatus.EXPIRED }

    // 主动智能按时钟推理，每 30 秒刷新一次，不阻塞主线程
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }
    val suggestions = remember(medicines, plans, todayChecked, now) {
        ProactiveEngine.suggestions(medicines, plans, todayChecked, now)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to HeroTint,
                    0.22f to Background,
                    1f to Background
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                // 顶部做一层很淡的青绿渐晕和两个光斑，让标题区不再是一块纯色
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Primary.copy(alpha = 0.11f), Color.Transparent)
                            )
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 52.dp, y = (-38).dp)
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.07f))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-46).dp, y = 54.dp)
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Accent.copy(alpha = 0.09f))
                    )
                    ScreenHeader(
                    eyebrow = "家庭药箱",
                    title = "我的药箱",
                    subtitle = "数据只存这台手机，离线也能用",
                    trailing = {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenReport),
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, Outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .padding(horizontal = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Assessment,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "体检卡",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Primary
                                )
                            }
                        }
                    }
                )
                }
            }
            // 长辈模式下收起洞察轮播，首屏只保留最重要的服药与统计
            if (insights.isNotEmpty() && !elderMode) {
                item {
                    InsightPager(insights = insights) { insight ->
                        when (insight.id) {
                            "expired" -> onOpenMedicines(StockFilter.EXPIRED)
                            "expiring" -> onOpenMedicines(StockFilter.EXPIRING)
                            else -> Unit
                        }
                    }
                }
            }
            item {
                if (medicines.isEmpty()) {
                    EmptyBoxCard(onOpenStarterKit = onOpenStarterKit)
                } else {
                    MedicineEntryCard(
                        total = medicines.size,
                        expiring = expiringCount,
                        expired = expiredCount
                    ) { onOpenMedicines(StockFilter.ALL) }
                }
            }
            item {
                TodayDoseCard(
                    plans = plans,
                    todayChecked = todayChecked,
                    weekDone = weekDone,
                    weekTotal = weekTotal,
                    onToggleCheck = onToggleCheck,
                    onAddPlan = onAddPlan,
                    onOpenReminders = onOpenReminders,
                    onOpenPacking = onOpenPacking
                )
            }
            if (suggestions.isNotEmpty()) {
                item {
                    ProactiveCard(
                        suggestions = suggestions,
                        onAction = { suggestion ->
                            when (val action = suggestion.action) {
                                is AgentAction.TakeDose -> onToggleCheck(action.plan, action.time)
                                is AgentAction.OpenMedicine -> medicines
                                    .firstOrNull { it.id == action.medicineId }
                                    ?.let(onSelectMedicine)
                                AgentAction.OpenReminders -> onOpenReminders()
                            }
                        }
                    )
                }
            }
        }
    }
}

/** 首页只留一个入口，完整清单在「全部药品」页。 */
@Composable
private fun MedicineEntryCard(
    total: Int,
    expiring: Int,
    expired: Int,
    onOpen: () -> Unit
) {
    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        padding = 14.dp,
        onClick = onOpen
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Primary.copy(alpha = 0.14f), Primary.copy(alpha = 0.24f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Inventory2,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "全部药品",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("共 $total 种")
                        if (expiring > 0) append(" · $expiring 种即将到期")
                        if (expired > 0) append(" · $expired 种已过期")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "查看全部",
                style = MaterialTheme.typography.labelMedium,
                color = Primary
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun InsightPager(
    insights: List<Insight>,
    onOpenInsight: (Insight) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { insights.size })

    Column {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 卡廊效果：下一张卡片在右侧露出约 24dp，提示可以继续滑动。
            val cardWidth = (maxWidth - 44.dp).coerceAtLeast(200.dp)
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 10.dp,
                pageSize = PageSize.Fixed(cardWidth)
            ) { page ->
                InsightCard(
                    insight = insights[page],
                    index = page,
                    total = insights.size
                ) { onOpenInsight(insights[page]) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            insights.indices.forEach { index ->
                val active = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (active) Primary else Primary.copy(alpha = 0.22f))
                )
            }
        }
    }
}

@Composable
private fun InsightCard(
    insight: Insight,
    index: Int,
    total: Int,
    onClick: () -> Unit
) {
    // 用浅色底 + 语义色点缀，替代整块高饱和渐变，避免抢占首页主次。
    val accent = when (insight.severity) {
        InsightSeverity.ALERT -> Danger
        InsightSeverity.WARNING -> AccentInk
        InsightSeverity.INFO -> Primary
    }
    val container = when (insight.severity) {
        InsightSeverity.ALERT -> DangerContainer
        InsightSeverity.WARNING -> AccentContainer
        InsightSeverity.INFO -> PrimaryContainer
    }
    val icon: ImageVector = when (insight.severity) {
        InsightSeverity.ALERT -> Icons.Rounded.Error
        InsightSeverity.WARNING -> Icons.Rounded.Schedule
        InsightSeverity.INFO -> Icons.Rounded.AutoAwesome
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .border(1.dp, accent.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceWhite.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (insight.severity == InsightSeverity.INFO) "主动洞察" else "需要注意",
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${index + 1} / $total",
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.55f)
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = insight.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = insight.detail,
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (insight.severity == InsightSeverity.INFO) "轻点查看相关药品 →" else "轻点筛选相关药品 →",
            style = MaterialTheme.typography.labelSmall,
            color = accent.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun TodayDoseCard(
    plans: List<MedicationPlan>,
    todayChecked: Set<String>,
    weekDone: Int,
    weekTotal: Int,
    onToggleCheck: (MedicationPlan, String) -> Unit,
    onAddPlan: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenPacking: () -> Unit
) {
    val today = DateUtils.today()
    val active = plans.filter { it.activeOn(today) }
    val total = active.sumOf { it.times.size }
    val done = active.sumOf { plan ->
        plan.times.count { plan.checkKey(it) in todayChecked }
    }

    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        padding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Medication,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "今日服药",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Text(
                    text = buildString {
                        append(DateUtils.monthDay(DateUtils.toIso(today)))
                        append(" · ")
                        append(if (weekTotal == 0) "本周暂无到点记录" else "本周 $weekDone/$weekTotal 次")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
            }
            if (total > 0) {
                Pill(
                    text = "$done/$total 已打卡",
                    container = if (done == total) PrimaryContainer else PrimaryContainer.copy(alpha = 0.55f),
                    content = Primary
                )
            }
        }

        if (active.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Background)
                    .clickable(onClick = onAddPlan)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "添加服药计划，到点本地提醒",
                    style = MaterialTheme.typography.labelLarge,
                    color = Primary
                )
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Divider)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (total == 0) 0f else done.toFloat() / total)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Primary)
                )
            }
            Spacer(Modifier.height(8.dp))
            // 多个计划混排时统一按时间排序，避免不同计划的条目插在中间。
            active.flatMap { plan -> plan.times.map { time -> plan to time } }
                .sortedBy { it.second }
                .forEach { (plan, time) ->
                    DoseRow(
                        plan = plan,
                        time = time,
                        checked = plan.checkKey(time) in todayChecked
                    ) { onToggleCheck(plan, time) }
                }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Divider)
            Spacer(Modifier.height(10.dp))
            // 动作靠左排，右侧留给悬浮按钮；窄屏可横向滑动，避免第三个入口被挤掉
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlanActionChip(
                    icon = Icons.Rounded.Add,
                    label = "添加计划",
                    accent = Primary,
                    onClick = onAddPlan
                )
                PlanActionChip(
                    icon = Icons.Rounded.NotificationsNone,
                    label = "提醒设置",
                    accent = InkSecondary,
                    onClick = onOpenReminders
                )
                PlanActionChip(
                    icon = Icons.Rounded.DateRange,
                    label = "分药盒",
                    accent = InkSecondary,
                    onClick = onOpenPacking
                )
            }
        }
    }
}

@Composable
private fun PlanActionChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, Outline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            maxLines = 1
        )
    }
}

@Composable
private fun DoseRow(
    plan: MedicationPlan,
    time: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (checked) PrimaryContainer else Background
        ) {
            Text(
                text = time,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (checked) Primary else InkSecondary
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plan.medicineName,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(plan.dose, plan.memberName.ifBlank { "全家" }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                // 胶囊视觉不变，热区最小 44dp
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(50))
                .background(if (checked) Primary else SurfaceWhite)
                .border(1.dp, if (checked) Primary else Outline, RoundedCornerShape(50))
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 5.dp),
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
                style = MaterialTheme.typography.labelSmall,
                color = if (checked) OnPrimary else Primary
            )
        }
    }
}

@Composable
private fun EmptyBoxCard(onOpenStarterKit: () -> Unit) {
    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        padding = 18.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Inventory2,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "药箱还是空的",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "第一次录入后，过期提醒和查重就有了依据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    lineHeight = 18.sp
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickEntryHint(Icons.Rounded.PhotoCamera, "拍药盒", Modifier.weight(1f))
            QuickEntryHint(Icons.Rounded.Mic, "说一句", Modifier.weight(1f))
            QuickEntryHint(Icons.Rounded.Edit, "手动填", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PrimaryContainer)
                .clickable(onClick = onOpenStarterKit)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "用药箱模板一键预填常备药",
                style = MaterialTheme.typography.labelLarge,
                color = Primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "去勾选 ›",
                style = MaterialTheme.typography.labelMedium,
                color = Primary
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "点右下角「＋」开始，所有数据只保存在这台手机上。",
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted
        )
    }
}

@Composable
private fun QuickEntryHint(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Background)
            .padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = InkSecondary)
    }
}

/**
 * 智能建议卡：编排层算出的「此刻该做什么」。
 * 只展示最重要的三条，每条都带一个能立刻执行的动作。
 */
@Composable
private fun ProactiveCard(
    suggestions: List<ProactiveSuggestion>,
    onAction: (ProactiveSuggestion) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "智能建议",
                style = MaterialTheme.typography.labelLarge,
                color = InkSecondary
            )
        }
        suggestions.forEachIndexed { index, suggestion ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            ProactiveSuggestionCard(suggestion) { onAction(suggestion) }
        }
    }
}

@Composable
private fun ProactiveSuggestionCard(
    suggestion: ProactiveSuggestion,
    onAction: () -> Unit
) {
    val (container, content) = when (suggestion.severity) {
        SuggestionSeverity.ALERT -> DangerContainer to Danger
        SuggestionSeverity.WARNING -> AccentContainer to AccentInk
        SuggestionSeverity.INFO -> PrimaryContainer to Primary
    }
    val icon = when (suggestion.event.type) {
        SceneEventType.DOSE_MISSED, SceneEventType.DOSE_DUE -> Icons.Rounded.Schedule
        SceneEventType.STOCK_RUNNING_OUT -> Icons.Rounded.Inventory2
        SceneEventType.EXPIRING_SOON -> Icons.Rounded.Error
    }

    SoftCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(container),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = suggestion.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = suggestion.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // 靠左放，右下角留给悬浮按钮，避免被遮挡
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(container)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = suggestion.actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = content
                    )
                }
            }
        }
    }
}
