package com.family.medbox.ui.home

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.StockStatus
import com.family.medbox.data.prefs.OpenedShelfLife
import com.family.medbox.ui.components.Pill
import com.family.medbox.ui.components.SectionHeader
import com.family.medbox.ui.components.SoftCard
import com.family.medbox.ui.theme.Accent
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
import com.family.medbox.ui.theme.SurfaceWhite
import com.family.medbox.util.DateUtils

/**
 * 开封效期：眼药水、糖浆、胰岛素这类药，开封后能用的时间比盒上有效期短得多。
 * 记录开封日，自动算出真实可用期，用完一键清掉。
 */
@Composable
fun OpenedShelfScreen(
    medicines: List<Medicine>,
    opened: Map<Long, String>,
    onMarkOpened: (Medicine) -> Unit,
    onClear: (Medicine) -> Unit,
    onClose: () -> Unit
) {
    // 整盒已经过期的药不建议再开封使用，直接走清理流程。
    val trackable = medicines.filter {
        OpenedShelfLife.shelfDays(it) != null && it.status != StockStatus.EXPIRED
    }
    val inUse = medicines.filter { opened.containsKey(it.id) }
        .sortedBy { OpenedShelfLife.daysLeft(it, opened.getValue(it.id)) ?: Long.MAX_VALUE }
    val waiting = trackable.filterNot { opened.containsKey(it.id) }
    val soon = inUse.count { (OpenedShelfLife.daysLeft(it, opened.getValue(it.id)) ?: 999L) <= 7L }

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
                        text = "开封效期",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink
                    )
                    Text(
                        text = "开封后，有效期要重新算",
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniStat(
                    value = inUse.size.toString(),
                    label = "开封中",
                    accent = Primary,
                    modifier = Modifier.weight(1f)
                )
                MiniStat(
                    value = soon.toString(),
                    label = "7 天内到期",
                    accent = if (soon > 0) AccentInk else InkMuted,
                    modifier = Modifier.weight(1f)
                )
                MiniStat(
                    value = waiting.size.toString(),
                    label = "待记开封",
                    accent = InkMuted,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionHeader(title = "开封中", trailing = "${inUse.size} 盒")
            if (inUse.isEmpty()) {
                SoftCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    padding = 18.dp
                ) {
                    Text(
                        text = "还没有开封记录。滴眼液、糖浆、胰岛素这些药，开封当天记一笔，之后就不会用过期药。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                        lineHeight = 19.sp
                    )
                }
            } else {
                inUse.forEach { medicine ->
                    OpenedCard(
                        medicine = medicine,
                        openedIso = opened.getValue(medicine.id),
                        onClear = { onClear(medicine) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            SectionHeader(title = "可以记录开封", trailing = "按剂型识别")
            if (waiting.isEmpty()) {
                Text(
                    text = "药箱里的眼药水、糖浆、胰岛素都已记录开封。",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                )
            } else {
                SoftCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    padding = 4.dp
                ) {
                    waiting.take(12).forEachIndexed { index, medicine ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .height(1.dp)
                                    .background(Divider)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = medicine.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${OpenedShelfLife.shelfDays(medicine)} 天开封效期 · " +
                                        (medicine.spec.ifBlank { medicine.form }.ifBlank { "未填规格" }),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(PrimaryContainer)
                                    .clickable { onMarkOpened(medicine) }
                                    .padding(horizontal = 11.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "今天开封",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Primary
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "开封效期按常见标准估算：滴眼液 28 天、糖浆 30 天、胰岛素 28 天。实际以说明书为准。",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun MiniStat(
    value: String,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceWhite)
            .padding(horizontal = 13.dp, vertical = 12.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
            color = accent
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted
        )
    }
}

@Composable
private fun OpenedCard(
    medicine: Medicine,
    openedIso: String,
    onClear: () -> Unit
) {
    val shelf = OpenedShelfLife.shelfDays(medicine) ?: return
    val used = OpenedShelfLife.usedDays(openedIso)
    val left = OpenedShelfLife.daysLeft(medicine, openedIso) ?: 0L
    val progress = (used.toFloat() / shelf).coerceIn(0f, 1f)
    val (container, content) = when {
        left < 0L -> DangerContainer to Danger
        left <= 7L -> AccentContainer to AccentInk
        else -> PrimaryContainer to Primary
    }
    val label = when {
        left < 0L -> "已过期 ${-left} 天"
        left == 0L -> "今天到期"
        else -> "还剩 $left 天"
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
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(container),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(17.dp)
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
                    text = "${DateUtils.monthDay(openedIso)} 开封 · 第 ${used + 1} 天 / 共 $shelf 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
            }
            Pill(text = label, container = container, content = content)
        }

        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(Divider)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (left <= 7L) Accent else Primary)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "按开封计算，可用到 ${DateUtils.displayDate(OpenedShelfLife.expireDate(medicine, openedIso))}",
                style = MaterialTheme.typography.labelSmall,
                color = InkSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "已用完",
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}
