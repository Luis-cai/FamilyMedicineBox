package com.family.medbox.ui.family

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.model.Member
import com.family.medbox.data.model.StockStatus
import com.family.medbox.data.sync.FamilyEvent
import com.family.medbox.data.sync.SyncInfo
import com.family.medbox.ui.components.ChoicePill
import com.family.medbox.ui.components.MemberAvatar
import com.family.medbox.ui.components.Pill
import com.family.medbox.ui.components.ScreenHeader
import com.family.medbox.ui.components.SectionHeader
import com.family.medbox.ui.components.SelectableChip
import com.family.medbox.ui.components.SoftCard
import com.family.medbox.ui.components.StatTile
import com.family.medbox.ui.theme.AccentContainer
import com.family.medbox.ui.theme.AccentInk
import com.family.medbox.ui.theme.Background
import com.family.medbox.ui.theme.Danger
import com.family.medbox.ui.theme.DangerContainer
import com.family.medbox.ui.theme.Divider
import com.family.medbox.ui.theme.Success
import com.family.medbox.ui.theme.Ink
import com.family.medbox.ui.theme.HeroTint
import com.family.medbox.ui.theme.InkMuted
import com.family.medbox.ui.theme.InkSecondary
import com.family.medbox.ui.theme.OnPrimary
import com.family.medbox.ui.theme.Primary
import com.family.medbox.ui.theme.PrimaryContainer
import com.family.medbox.ui.theme.SurfaceWhite
import com.family.medbox.util.DateUtils
import kotlin.math.abs
import kotlin.math.roundToInt

/** 长辈模式字号档位：标签 to 放大倍数 */
private val ELDER_FONT_OPTIONS = listOf(
    "大" to 1.2f,
    "特大" to 1.35f,
    "超大" to 1.5f
)

@Composable
fun FamilyScreen(
    members: List<Member>,
    medicines: List<Medicine>,
    plans: List<MedicationPlan>,
    todayChecked: Set<String>,
    syncInfo: SyncInfo,
    pendingSync: Int,
    familyEvents: List<FamilyEvent>,
    onAddMember: (String, String, Boolean) -> Unit,
    onToggleCare: (Member) -> Unit,
    onDeleteMember: (Member) -> Unit,
    onOpenMember: (Member) -> Unit,
    onOpenEmergency: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenFollowUp: () -> Unit,
    onOpenFoodGuide: () -> Unit,
    onOpenOpened: () -> Unit,
    onOpenElder: () -> Unit,
    elderMode: Boolean,
    onToggleElderMode: () -> Unit,
    elderFontScale: Float,
    onElderFontScaleChange: (Float) -> Unit,
    highContrast: Boolean,
    onToggleHighContrast: () -> Unit,
    onOpenSync: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Member?>(null) }
    val today = DateUtils.today()
    val careMembers = members.filter { it.needsCare }

    // 需要照护的成员：今天的计划次数与已打卡次数，家人一眼能看出还差几次。
    fun todayDoses(member: Member): Pair<Int, Int> {
        val own = plans.filter { it.memberId == member.id && it.activeOn(today) }
        val total = own.sumOf { it.times.size }
        val done = own.sumOf { plan -> plan.times.count { "${plan.id}@$it" in todayChecked } }
        return done to total
    }

    val expiring = medicines.count { it.status == StockStatus.EXPIRING }
    val expired = medicines.count { it.status == StockStatus.EXPIRED }
    val syncSubtitle = when {
        !syncInfo.loggedIn -> "未连接 · 开启后家人的手机也能收到提醒"
        !syncInfo.inFamily -> "已登录 ${syncInfo.nickname} · 待创建或加入家庭组"
        !syncInfo.enabled -> "已加入「${syncInfo.familyName}」· 自动同步已关闭"
        pendingSync > 0 -> "已加入「${syncInfo.familyName}」· $pendingSync 项改动待上传"
        else -> "已加入「${syncInfo.familyName}」· 邀请码 ${syncInfo.inviteCode}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to HeroTint,
                    0.28f to Background,
                    1f to Background
                )
            )
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            // 底部导航是悬浮覆盖在内容之上的，留出高度避免最后一张卡片被压住。
            .padding(bottom = 100.dp)
    ) {
        ScreenHeader(
            eyebrow = "家的用药记忆",
            title = "家庭成员",
            subtitle = "${members.size} 位成员 · 一起管理全家用药"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                value = medicines.size.toString(),
                label = "在库药品",
                accent = Primary,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = expiring.toString(),
                label = "30 天内到期",
                accent = AccentInk,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = expired.toString(),
                label = "已过期",
                accent = Danger,
                modifier = Modifier.weight(1f)
            )
        }

        SoftCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            padding = 14.dp,
            onClick = onOpenEmergency
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(DangerContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = Danger,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "家庭急救卡",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink
                    )
                    Text(
                        text = "过敏史 · 慢病 · 当前用药 · 紧急联系人",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = InkMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        SoftCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            padding = 6.dp
        ) {
            FamilyEntryRow(
                icon = Icons.AutoMirrored.Rounded.ShowChart,
                tint = Primary,
                container = PrimaryContainer,
                title = "指标日记",
                subtitle = "血压 · 血糖 · 体温，一键生成就诊摘要",
                onClick = onOpenJournal
            )
            EntryDivider()
            FamilyEntryRow(
                icon = Icons.Rounded.EventAvailable,
                tint = AccentInk,
                container = AccentContainer,
                title = "复诊 · 续方",
                subtitle = "下次复诊倒计时，处方到期前提醒",
                onClick = onOpenFollowUp
            )
            EntryDivider()
            FamilyEntryRow(
                icon = Icons.Rounded.Restaurant,
                tint = Danger,
                container = DangerContainer,
                title = "服药饮食禁忌",
                subtitle = "按在服药品匹配酒、西柚、牛奶等",
                onClick = onOpenFoodGuide
            )
            EntryDivider()
            FamilyEntryRow(
                icon = Icons.Rounded.Schedule,
                tint = Primary,
                container = PrimaryContainer,
                title = "开封效期",
                subtitle = "眼药水、糖浆开封后重新计时",
                onClick = onOpenOpened
            )
        }

        if (familyEvents.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Timeline,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "家庭动态",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSecondary
                    )
                }
                SoftCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
                    Column {
                        familyEvents.take(3).forEachIndexed { index, event ->
                            if (index > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .height(1.dp)
                                        .background(Divider)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (event.isDoseTaken) Success else InkMuted)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = event.title(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Ink
                                    )
                                    Text(
                                        text = listOfNotNull(
                                            event.timeLabel(),
                                            event.medicineName.takeIf { it.isNotBlank() }
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SectionHeader(
            title = "成员与其药品",
            action = {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PrimaryContainer)
                        .clickable { showAddDialog = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("添加", style = MaterialTheme.typography.labelMedium, color = Primary)
                }
            }
        )

        Spacer(Modifier.height(4.dp))

        members.forEach { member ->
            val own = medicines.filter { it.memberId == member.id }
            val ownExpiring = own.count { it.status != StockStatus.NORMAL }
            val (careDone, careTotal) = if (member.needsCare) todayDoses(member) else 0 to 0
            val careMissed = member.needsCare && careTotal > 0 && careDone < careTotal
            // 「本人」标签就在名字旁，副标题不再重复写关系
            val subtitle = if (member.isSelf) "${own.size} 种药品" else "${member.relation} · ${own.size} 种药品"
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 3.dp),
                onClick = { onOpenMember(member) },
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, Divider)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MemberAvatar(
                            text = member.name,
                            colorIndex = member.colorIndex,
                            size = 40.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = member.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Ink
                                )
                                if (member.isSelf) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(PrimaryContainer)
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            "本人",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Primary
                                        )
                                    }
                                }
                            }
                        }
                        when {
                            careMissed -> Pill(
                                text = "还差 ${careTotal - careDone} 次",
                                container = DangerContainer,
                                content = Danger
                            )
                            member.needsCare -> Pill(
                                text = "重点关注",
                                container = PrimaryContainer,
                                content = Primary
                            )
                            ownExpiring > 0 -> Pill(
                                text = "$ownExpiring 需注意",
                                container = AccentContainer,
                                content = AccentInk
                            )
                        }
                        if ((careMissed || member.needsCare || ownExpiring > 0)) {
                            Spacer(Modifier.width(8.dp))
                        }
                        if (!member.isSelf) {
                            // 图标本身只有 18dp，外圈 44dp 撑足点击热区，避免老人/单手误触。
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onToggleCare(member) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (member.needsCare) Icons.Rounded.Favorite
                                    else Icons.Rounded.FavoriteBorder,
                                    contentDescription = if (member.needsCare) "取消重点关注"
                                    else "设为重点关注",
                                    tint = if (member.needsCare) Primary else InkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable { pendingDelete = member },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "删除成员",
                                    tint = InkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // 副标题单独占一行，长辈模式放大字号也不会被胶囊和图标挤断
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 52.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "照护建议",
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary
                        )
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (careMembers.isNotEmpty()) {
            SoftCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                padding = 14.dp,
                onClick = onOpenElder
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "老人关怀 · 大字打卡",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${careMembers.joinToString("、") { it.name }}的今日用药，" +
                                "打开就是大按钮，点一下就算吃过",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                            maxLines = 2
                        )
                    }
                    Text(
                        text = "打开",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        SoftCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            padding = 14.dp
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.TextIncrease,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "长辈模式",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (elderMode) {
                                "字号 ${(elderFontScale * 100).roundToInt()}% · 首页收起提醒流"
                            } else {
                                "字体放大、对比度增强，首页更简洁"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                            maxLines = 2
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = elderMode,
                        onCheckedChange = { onToggleElderMode() }
                    )
                }

                if (elderMode) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Divider)
                    )
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "字号",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ELDER_FONT_OPTIONS.forEach { option ->
                            SelectableChip(
                                label = option.first,
                                selected = abs(elderFontScale - option.second) < 0.01f,
                                onClick = { onElderFontScaleChange(option.second) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "对比度增强",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ink
                            )
                            Text(
                                text = "加深次要文字与分隔线，弱视也看得清",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = highContrast,
                            onCheckedChange = { onToggleHighContrast() }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        SoftCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            padding = 14.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(PrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.CloudSync,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "家庭同步",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = syncSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                        maxLines = 2
                    )
                }
                Text(
                    text = if (syncInfo.inFamily) "管理" else "设置",
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onOpenSync() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        SoftCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            padding = 14.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(PrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "隐私与数据",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "药箱清单、照片和语音只存本机；开启家庭同步后，仅上传服药计划与打卡状态。" +
                    "换手机时可用下方备份迁移，文件由你自己保管。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onExportBackup,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("导出备份", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onImportBackup,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("从备份恢复", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showAddDialog) {
        AddMemberDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, relation, needsCare ->
                onAddMember(name, relation, needsCare)
                showAddDialog = false
            }
        )
    }

    pendingDelete?.let { member ->
        Dialog(onDismissRequest = { pendingDelete = null }) {
            Surface(shape = RoundedCornerShape(22.dp), color = SurfaceWhite) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("删除成员", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "删除「${member.name}」后，该成员名下的药品会变为" +
                            "未分配成员，药品记录本身不会丢失。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary,
                        lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(
                            onClick = { pendingDelete = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("取消", color = InkMuted)
                        }
                        TextButton(
                            onClick = {
                                onDeleteMember(member)
                                pendingDelete = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("删除", color = Danger)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMemberDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var needsCare by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val presets = listOf(
        "爷爷" to "祖父",
        "奶奶" to "祖母",
        "外公" to "外祖父",
        "外婆" to "外祖母",
        "爸爸" to "父亲",
        "妈妈" to "母亲",
        "孩子" to "孩子"
    )
    val elderNames = setOf("爷爷", "奶奶", "外公", "外婆")

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = SurfaceWhite) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("添加家庭成员", style = MaterialTheme.typography.titleLarge, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "用于把药品区分到具体的人",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted
                )
                Spacer(Modifier.height(12.dp))

                Text("常用称呼", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                Spacer(Modifier.height(6.dp))
                presets.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, rel) ->
                            ChoicePill(
                                label = label,
                                selected = name == label,
                                onClick = {
                                    name = label
                                    relation = rel
                                    if (label in elderNames) needsCare = true
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                DialogField(
                    label = "称呼",
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "如：奶奶"
                )
                DialogField(
                    label = "关系",
                    value = relation,
                    onValueChange = { relation = it },
                    placeholder = "如：祖母"
                )

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "记性不太好，需要重点关注",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink
                        )
                        Text(
                            text = "到点未打卡会在 15、30 分钟后各补一次提醒",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                    Switch(
                        checked = needsCare,
                        onCheckedChange = { needsCare = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnPrimary,
                            checkedTrackColor = Primary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Divider,
                            uncheckedBorderColor = Divider
                        )
                    )
                }

                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Danger
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消", color = InkMuted)
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (name.isBlank()) {
                                    error = "请填写称呼"
                                } else {
                                    onConfirm(
                                        name.trim(),
                                        relation.trim().ifBlank { "家庭成员" },
                                        needsCare
                                    )
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = Primary
                    ) {
                        Text(
                            text = "确定",
                            modifier = Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = OnPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkMuted)
        Spacer(Modifier.height(5.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Background,
            border = BorderStroke(1.dp, Divider)
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** 健康档案里的一行入口：图标 + 标题 + 说明 + 右箭头。 */
@Composable
private fun FamilyEntryRow(
    icon: ImageVector,
    tint: Color,
    container: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Ink
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = InkMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun EntryDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 58.dp, end = 6.dp)
            .height(1.dp)
            .background(Divider)
    )
}
