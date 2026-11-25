package com.family.medbox.ui.family

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.Member
import com.family.medbox.data.model.StockStatus
import com.family.medbox.data.prefs.FollowUp
import com.family.medbox.domain.CareTip
import com.family.medbox.domain.CareTone
import com.family.medbox.domain.DrugKnowledge
import com.family.medbox.domain.MemberCareGuide
import com.family.medbox.ui.components.ExpiryBadge
import com.family.medbox.ui.components.IconBadge
import com.family.medbox.ui.components.MemberAvatar
import com.family.medbox.ui.components.Pill
import com.family.medbox.ui.components.SectionHeader
import com.family.medbox.ui.components.SoftCard
import com.family.medbox.ui.theme.AccentContainer
import com.family.medbox.ui.theme.AccentInk
import com.family.medbox.ui.theme.Background
import com.family.medbox.ui.theme.Danger
import com.family.medbox.ui.theme.DangerContainer
import com.family.medbox.ui.theme.Divider
import com.family.medbox.ui.theme.Ink
import com.family.medbox.ui.theme.InkMuted
import com.family.medbox.ui.theme.InkSecondary
import com.family.medbox.ui.theme.Primary
import com.family.medbox.ui.theme.PrimaryContainer
import com.family.medbox.ui.theme.Success
import com.family.medbox.ui.theme.SuccessContainer
import com.family.medbox.ui.theme.SurfaceWhite
import com.family.medbox.util.DateUtils

/**
 * 成员照护页：点开某位成员后，按他名下的药给出
 * 「该做什么 / 不该做什么 / 需要注意」三组具体建议。
 */
@Composable
fun MemberCareScreen(
    member: Member,
    medicines: List<Medicine>,
    plans: List<MedicationPlan>,
    checks: Map<String, Set<String>>,
    opened: Map<Long, String>,
    followUps: List<FollowUp>,
    knowledge: DrugKnowledge,
    onClose: () -> Unit
) {
    val today = DateUtils.today()
    val own = remember(medicines, member.id) { medicines.filter { it.memberId == member.id } }
    val activePlans = remember(plans, member.id, today) {
        plans.filter { it.memberId == member.id && it.activeOn(today) }
    }
    val checked = checks[DateUtils.toIso(today)].orEmpty()
    val total = activePlans.sumOf { it.times.size }
    val done = activePlans.sumOf { plan -> plan.times.count { plan.checkKey(it) in checked } }
    val guide = remember(member, own, plans, checks, opened, followUps) {
        MemberCareGuide.build(member, medicines, plans, checks, opened, followUps, knowledge)
    }
    val expiring = own.count { it.status == StockStatus.EXPIRING }
    val expired = own.count { it.status == StockStatus.EXPIRED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        Surface(color = SurfaceWhite) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = Ink
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${member.name}的照护建议",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "按名下 ${own.size} 种药品自动整理",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 14.dp)
        ) {
            // 成员概览：今天该吃几次、还差几次
            SoftCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                padding = 16.dp,
                background = PrimaryContainer
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemberAvatar(text = member.name, colorIndex = member.colorIndex, size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = member.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = Ink
                            )
                            Spacer(Modifier.width(6.dp))
                            Pill(
                                text = member.relation,
                                container = SurfaceWhite,
                                content = InkSecondary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (member.needsCare) {
                                "重点关注 · 漏服会多次补提醒"
                            } else {
                                "${own.size} 种药品 · $total 次今日服药"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSecondary
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat(
                        value = if (total == 0) "—" else "$done/$total",
                        label = "今日已打卡",
                        accent = Primary,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStat(
                        value = own.size.toString(),
                        label = "名下药品",
                        accent = Ink,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStat(
                        value = (expiring + expired).toString(),
                        label = "需处理效期",
                        accent = if (expired > 0) Danger else AccentInk,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            GuideSection(
                title = "该做什么",
                subtitle = "每天照着做就行",
                icon = Icons.Rounded.CheckCircle,
                tint = Success,
                container = SuccessContainer,
                tips = guide.mustDo
            )
            GuideSection(
                title = "不该做什么",
                subtitle = "先避开这些，减少风险",
                icon = Icons.Rounded.Block,
                tint = Danger,
                container = DangerContainer,
                tips = guide.avoid
            )
            GuideSection(
                title = "需要注意什么",
                subtitle = "出现这些情况多留意",
                icon = Icons.Rounded.Info,
                tint = AccentInk,
                container = AccentContainer,
                tips = guide.watch
            )

            if (own.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                SectionHeader(title = "${member.name}名下的药品")
                own.forEach { medicine ->
                    SoftCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 3.dp),
                        padding = 12.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (medicine.usable) PrimaryContainer else DangerContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = medicine.name.take(1),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (medicine.usable) Primary else Danger
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = medicine.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = listOfNotNull(
                                        medicine.summary.takeIf { it.isNotBlank() },
                                        medicine.stockLabel
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            ExpiryBadge(medicine)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "以上建议由本机药品知识库和常见用药规则自动整理，只作提醒。" +
                    "处方药的用法、停药和换药，请以医生和药师的交代为准。",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun MiniStat(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceWhite)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = accent)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = InkMuted)
    }
}

@Composable
private fun GuideSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    container: Color,
    tips: List<CareTip>
) {
    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        padding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = icon, tint = tint, background = container, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
            }
            Text(
                text = "${tips.size} 条",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted
            )
        }

        if (tips.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "暂时没有额外的提醒。把药品和服药计划补全后，这里会更具体。",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                lineHeight = 19.sp
            )
        } else {
            tips.forEachIndexed { index, tip ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .height(1.dp)
                            .background(Divider)
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                }
                TipRow(tip = tip, fallback = tint)
            }
        }
    }
}

@Composable
private fun TipRow(tip: CareTip, fallback: Color) {
    val dot = when (tip.tone) {
        CareTone.DANGER -> Danger
        CareTone.WARN -> AccentInk
        CareTone.NORMAL -> fallback
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tip.title,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = tip.detail,
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                lineHeight = 19.sp
            )
        }
    }
}
