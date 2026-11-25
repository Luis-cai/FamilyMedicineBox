package com.family.medbox.ui.family

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.model.Member
import com.family.medbox.data.prefs.HealthKind
import com.family.medbox.data.prefs.HealthRecord
import com.family.medbox.ui.components.MemberAvatar
import com.family.medbox.ui.components.Pill
import com.family.medbox.ui.components.PrimaryButton
import com.family.medbox.ui.components.SectionHeader
import com.family.medbox.ui.components.SelectableChip
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
import com.family.medbox.ui.theme.Outline
import com.family.medbox.ui.theme.Primary
import com.family.medbox.ui.theme.PrimaryContainer
import com.family.medbox.ui.theme.Success
import com.family.medbox.ui.theme.SuccessContainer
import com.family.medbox.ui.theme.SurfaceWhite
import com.family.medbox.util.DateUtils
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 指标日记：按成员记录血压 / 血糖 / 体温 / 症状，给出最近趋势，
 * 并能把近 30 天情况整理成一段可直接发给医生的就诊摘要。
 */
@Composable
fun HealthJournalScreen(
    members: List<Member>,
    records: List<HealthRecord>,
    plans: List<MedicationPlan>,
    onAdd: (HealthRecord) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf(members.firstOrNull()?.id ?: 0L) }
    var adding by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<HealthRecord?>(null) }
    var summary by remember { mutableStateOf<String?>(null) }

    val member = members.firstOrNull { it.id == selectedId } ?: members.firstOrNull()
    val mine = remember(records, selectedId) {
        records.filter { it.memberId == selectedId }
    }
    val kindStats = remember(mine) {
        HealthKind.values().associateWith { kind -> mine.filter { it.kind == kind } }
    }
    var chartKind by remember { mutableStateOf(HealthKind.BP) }

    fun share(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "就诊摘要")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "分享就诊摘要"))
    }

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
                    .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
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
                        text = "指标日记",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink
                    )
                    Text(
                        text = "血压 · 血糖 · 体温 · 症状",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PrimaryContainer)
                        .clickable { adding = true }
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
                    Text("记录", style = MaterialTheme.typography.labelMedium, color = Primary)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            if (members.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                SoftCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    padding = 18.dp
                ) {
                    Text(
                        text = "还没有家庭成员。先在家庭页添加成员，再记录身体指标。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
                return@Column
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                members.forEach { m ->
                    val selected = m.id == member?.id
                    SelectableChip(
                        label = m.name,
                        selected = selected,
                        onClick = { selectedId = m.id },
                        leading = {
                            MemberAvatar(
                                text = m.avatarText,
                                colorIndex = m.colorIndex,
                                size = 22.dp,
                                selected = selected
                            )
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    kind = HealthKind.BP,
                    latest = kindStats[HealthKind.BP]?.firstOrNull(),
                    previous = kindStats[HealthKind.BP]?.getOrNull(1),
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    kind = HealthKind.GLUCOSE,
                    latest = kindStats[HealthKind.GLUCOSE]?.firstOrNull(),
                    previous = kindStats[HealthKind.GLUCOSE]?.getOrNull(1),
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    kind = HealthKind.TEMPERATURE,
                    latest = kindStats[HealthKind.TEMPERATURE]?.firstOrNull(),
                    previous = kindStats[HealthKind.TEMPERATURE]?.getOrNull(1),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader(title = "趋势", trailing = "最近 14 次")
            SoftCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                padding = 16.dp
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(HealthKind.BP, HealthKind.GLUCOSE, HealthKind.TEMPERATURE).forEach { kind ->
                        SelectableChip(
                            label = kind.label,
                            selected = chartKind == kind,
                            onClick = { chartKind = kind }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                val points = kindStats[chartKind].orEmpty().take(14).reversed()
                if (points.size < 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "至少记录 2 次${chartKind.label}，就能看到变化",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                    }
                } else {
                    TrendChart(
                        values = points.mapNotNull { it.chartValue }.map { it.toFloat() },
                        accent = kindColor(chartKind),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(118.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    val nums = points.mapNotNull { it.chartValue }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TrendStat("平均", "%.1f".format(nums.average()))
                        TrendStat("最高", "%.1f".format(nums.max()))
                        TrendStat("最低", "%.1f".format(nums.min()))
                        TrendStat("次数", "${points.size}")
                    }
                    if (chartKind == HealthKind.BP) {
                        Text(
                            text = "曲线取收缩压（高压），舒张压见下方记录",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SoftCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                padding = 16.dp,
                onClick = {
                    summary = buildVisitSummary(member, mine, plans)
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.EditNote,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "生成就诊摘要",
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink
                        )
                        Text(
                            text = "近 30 天指标 + 在服药物，一键发给医生",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(title = "记录", trailing = "${mine.size} 条")
            if (mine.isEmpty()) {
                SoftCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    padding = 20.dp
                ) {
                    Text(
                        text = "还没有${member?.name.orEmpty()}的记录。点右上角「记录」，量完血压或血糖顺手记一笔。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                        lineHeight = 19.sp
                    )
                }
            } else {
                mine.groupBy { it.dateIso }.forEach { (dateIso, dayRecords) ->
                    Text(
                        text = dateLabel(dateIso),
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSecondary,
                        modifier = Modifier.padding(start = 24.dp, top = 6.dp, bottom = 6.dp)
                    )
                    SoftCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        padding = 4.dp
                    ) {
                        dayRecords.forEachIndexed { index, record ->
                            if (index > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .height(1.dp)
                                        .background(Divider)
                                )
                            }
                            RecordRow(record = record, onDelete = { pendingDelete = record })
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AddRecordDialog(
            members = members,
            defaultMember = member,
            onDismiss = { adding = false },
            onSave = { record ->
                onAdd(record)
                selectedId = record.memberId
                adding = false
            }
        )
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = SurfaceWhite,
            title = { Text("删除这条记录？", color = Ink) },
            text = {
                Text(
                    text = "${DateUtils.monthDay(record.dateIso)} ${record.timeLabel} · " +
                        "${record.kind.label} ${record.displayValue}${record.kind.unit}",
                    color = InkSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(record.id)
                        pendingDelete = null
                    }
                ) { Text("删除", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = InkMuted)
                }
            }
        )
    }

    summary?.let { text ->
        Dialog(onDismissRequest = { summary = null }) {
            Surface(shape = RoundedCornerShape(24.dp), color = SurfaceWhite) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.EditNote,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "就诊摘要",
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                        lineHeight = 19.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val clipboard =
                                        context.getSystemService(ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(
                                        ClipData.newPlainText("就诊摘要", text)
                                    )
                                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = PrimaryContainer
                        ) {
                            Text(
                                text = "复制",
                                modifier = Modifier.padding(vertical = 13.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = Primary,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        PrimaryButton(
                            text = "分享",
                            onClick = { share(text) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "内容仅来自本机记录，供就诊沟通参考。",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    kind: HealthKind,
    latest: HealthRecord?,
    previous: HealthRecord?,
    modifier: Modifier = Modifier
) {
    val accent = kindColor(kind)
    val delta = if (latest?.chartValue != null && previous?.chartValue != null) {
        latest.chartValue!! - previous.chartValue!!
    } else {
        null
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceWhite)
            .padding(horizontal = 11.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = kind.label,
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = latest?.displayValue ?: "—",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
            color = if (latest == null) InkMuted else accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        when {
            latest == null -> Text(
                text = "未记录",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted
            )
            latest.abnormal -> Text(
                text = latest.abnormalLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Danger
            )
            delta != null && abs(delta) >= 0.05 -> Text(
                text = (if (delta > 0) "↑ " else "↓ ") + "%.1f".format(abs(delta)),
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted
            )
            else -> Text(
                text = DateUtils.monthDay(latest.dateIso),
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted
            )
        }
    }
}

@Composable
private fun TrendStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = Ink
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted
        )
    }
}

@Composable
private fun TrendChart(values: List<Float>, accent: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val divider = Divider
    Canvas(modifier = modifier) {
        // 三条水平参考线，让曲线有落点
        for (i in 0..3) {
            val y = size.height * i / 3f
            drawLine(
                color = divider,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0.01f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val padY = 8.dp.toPx()
        val usable = size.height - padY * 2
        val coords = values.mapIndexed { index, value ->
            Offset(
                x = index * stepX,
                y = padY + (1f - (value - min) / span) * usable
            )
        }
        val path = Path().apply {
            moveTo(coords.first().x, coords.first().y)
            coords.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = path,
            color = accent,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        coords.forEach { drawCircle(color = accent, radius = 3.dp.toPx(), center = it) }
    }
}

@Composable
private fun RecordRow(record: HealthRecord, onDelete: () -> Unit) {
    val accent = kindColor(record.kind)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 2.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = kindIcon(record.kind),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.kind.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = record.timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
                if (record.abnormal) {
                    Spacer(Modifier.width(6.dp))
                    Pill(
                        text = record.abnormalLabel,
                        container = DangerContainer,
                        content = Danger
                    )
                }
            }
            Spacer(Modifier.height(1.dp))
            Text(
                text = buildString {
                    if (record.kind == HealthKind.SYMPTOM) {
                        append(record.note.ifBlank { "未填写" })
                    } else {
                        append(record.displayValue)
                        append(" ")
                        append(record.kind.unit)
                        if (record.note.isNotBlank()) {
                            append(" · ")
                            append(record.note)
                        }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "删除",
                tint = InkMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AddRecordDialog(
    members: List<Member>,
    defaultMember: Member?,
    onDismiss: () -> Unit,
    onSave: (HealthRecord) -> Unit
) {
    var memberId by remember { mutableStateOf(defaultMember?.id ?: members.firstOrNull()?.id ?: 0L) }
    var kind by remember { mutableStateOf(HealthKind.BP) }
    var dateIso by remember { mutableStateOf(DateUtils.toIso(DateUtils.today())) }
    var systolic by remember { mutableStateOf("") }
    var diastolic by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val sys = systolic.toIntOrNull()
    val dia = diastolic.toIntOrNull()
    val num = value.replace(',', '.').toDoubleOrNull()
    val valid = when (kind) {
        HealthKind.BP -> sys != null && dia != null && sys in 50..260 && dia in 20..180 && sys > dia
        HealthKind.GLUCOSE -> num != null && num in 1.0..40.0
        HealthKind.TEMPERATURE -> num != null && num in 30.0..45.0
        HealthKind.SYMPTOM -> note.isNotBlank()
    }
    val member = members.firstOrNull { it.id == memberId }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = SurfaceWhite) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text("记录一条指标", style = MaterialTheme.typography.titleLarge, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "数据只保存在本机，可以随时删除",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HealthKind.values().forEach { item ->
                        SelectableChip(
                            label = item.label,
                            selected = kind == item,
                            onClick = { kind = item }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    members.forEach { m ->
                        val selected = m.id == memberId
                        SelectableChip(
                            label = m.name,
                            selected = selected,
                            onClick = { memberId = m.id },
                            leading = {
                                MemberAvatar(
                                    text = m.avatarText,
                                    colorIndex = m.colorIndex,
                                    size = 20.dp,
                                    selected = selected
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val today = DateUtils.today()
                    listOf("今天" to today, "昨天" to today.minusDays(1), "前天" to today.minusDays(2))
                        .forEach { (label, date) ->
                            val iso = DateUtils.toIso(date)
                            SelectableChip(
                                label = label,
                                selected = dateIso == iso,
                                onClick = { dateIso = iso }
                            )
                        }
                }

                Spacer(Modifier.height(14.dp))
                when (kind) {
                    HealthKind.BP -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(
                            label = "收缩压",
                            value = systolic,
                            suffix = "mmHg",
                            onValueChange = { systolic = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.weight(1f)
                        )
                        NumberField(
                            label = "舒张压",
                            value = diastolic,
                            suffix = "mmHg",
                            onValueChange = { diastolic = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HealthKind.GLUCOSE -> NumberField(
                        label = "血糖",
                        value = value,
                        suffix = "mmol/L",
                        allowDecimal = true,
                        onValueChange = { value = cleanDecimal(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HealthKind.TEMPERATURE -> NumberField(
                        label = "体温",
                        value = value,
                        suffix = "℃",
                        allowDecimal = true,
                        onValueChange = { value = cleanDecimal(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HealthKind.SYMPTOM -> OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("哪里不舒服") },
                        placeholder = { Text("如：早上头晕，持续半小时") },
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (kind != HealthKind.SYMPTOM) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("备注（可选）") },
                        placeholder = { Text("如：饭后、运动后、服药前") },
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onDismiss() },
                        shape = RoundedCornerShape(12.dp),
                        color = Background,
                        border = BorderStroke(1.dp, Outline)
                    ) {
                        Text(
                            text = "取消",
                            modifier = Modifier.padding(vertical = 13.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = InkSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                    PrimaryButton(
                        text = "保存",
                        enabled = valid && member != null,
                        onClick = {
                            if (member == null) return@PrimaryButton
                            onSave(
                                HealthRecord(
                                    id = UUID.randomUUID().toString(),
                                    memberId = member.id,
                                    memberName = member.name,
                                    kind = kind,
                                    dateIso = dateIso,
                                    timeLabel = DateUtils.chatTimeStamp(),
                                    systolic = if (kind == HealthKind.BP) sys ?: 0 else 0,
                                    diastolic = if (kind == HealthKind.BP) dia ?: 0 else 0,
                                    value = if (kind == HealthKind.GLUCOSE || kind == HealthKind.TEMPERATURE) {
                                        num ?: 0.0
                                    } else {
                                        0.0
                                    },
                                    note = note.trim()
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    suffix: String,
    modifier: Modifier = Modifier,
    allowDecimal: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text(suffix, color = InkMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        colors = fieldColors(),
        modifier = modifier
    )
}

@Composable
private fun fieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = Outline,
    focusedLabelColor = Primary,
    unfocusedLabelColor = InkMuted,
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    cursorColor = Primary,
    focusedContainerColor = SurfaceWhite,
    unfocusedContainerColor = SurfaceWhite
)

private fun cleanDecimal(raw: String): String {
    val filtered = raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
    val firstDot = filtered.indexOf('.')
    if (firstDot < 0) return filtered.take(3)
    val head = filtered.substring(0, firstDot).take(3)
    val tail = filtered.substring(firstDot + 1).filter(Char::isDigit).take(1)
    return "$head.$tail"
}

private fun kindColor(kind: HealthKind) = when (kind) {
    HealthKind.BP -> Danger
    HealthKind.GLUCOSE -> Accent
    HealthKind.TEMPERATURE -> Primary
    HealthKind.SYMPTOM -> InkSecondary
}

private fun kindIcon(kind: HealthKind) = when (kind) {
    HealthKind.BP -> Icons.AutoMirrored.Rounded.ShowChart
    HealthKind.GLUCOSE -> Icons.Rounded.WaterDrop
    HealthKind.TEMPERATURE -> Icons.Rounded.Thermostat
    HealthKind.SYMPTOM -> Icons.Rounded.EditNote
}

private fun dateLabel(iso: String): String {
    val date = DateUtils.parseIso(iso) ?: return iso
    val today = DateUtils.today()
    return when (date) {
        today -> "今天 · ${DateUtils.monthDay(iso)}"
        today.minusDays(1) -> "昨天 · ${DateUtils.monthDay(iso)}"
        today.minusDays(2) -> "前天 · ${DateUtils.monthDay(iso)}"
        else -> DateUtils.monthDay(iso)
    }
}

/** 把近 30 天的指标、异常、在服药物和换药节点整理成一段文字。 */
private fun buildVisitSummary(
    member: Member?,
    records: List<HealthRecord>,
    plans: List<MedicationPlan>
): String {
    val today = DateUtils.today()
    val from = today.minusDays(29)
    val recent = records.filter { record ->
        DateUtils.parseIso(record.dateIso)?.let { !it.isBefore(from) } ?: false
    }
    val excerpts = StringBuilder()
    excerpts.appendLine("【就诊摘要】${member?.name ?: "家庭成员"}")
    excerpts.append("汇总时间：${DateUtils.displayDate(DateUtils.toIso(today))}")
    excerpts.appendLine()
    excerpts.appendLine()

    excerpts.appendLine("一、近 30 天指标")
    var hasMetric = false
    HealthKind.values().forEach { kind ->
        val list = recent.filter { it.kind == kind }
        if (list.isEmpty()) return@forEach
        hasMetric = true
        when (kind) {
            HealthKind.BP -> {
                val avgSys = list.map { it.systolic }.average().roundToInt()
                val avgDia = list.map { it.diastolic }.average().roundToInt()
                val highest = list.maxByOrNull { it.systolic }
                val lowest = list.minByOrNull { it.systolic }
                excerpts.appendLine(
                    "· 血压 ${list.size} 次：平均 $avgSys/$avgDia mmHg，" +
                        "最高 ${highest?.systolic}/${highest?.diastolic}（${DateUtils.monthDay(highest?.dateIso)}），" +
                        "最低 ${lowest?.systolic}/${lowest?.diastolic}（${DateUtils.monthDay(lowest?.dateIso)}）"
                )
            }
            HealthKind.GLUCOSE -> {
                val nums = list.map { it.value }
                excerpts.appendLine(
                    "· 血糖 ${list.size} 次：平均 ${"%.1f".format(nums.average())} mmol/L，" +
                        "范围 ${"%.1f".format(nums.min())}–${"%.1f".format(nums.max())}"
                )
            }
            HealthKind.TEMPERATURE -> {
                val nums = list.map { it.value }
                excerpts.appendLine(
                    "· 体温 ${list.size} 次：平均 ${"%.1f".format(nums.average())} ℃，" +
                        "最高 ${"%.1f".format(nums.max())} ℃"
                )
            }
            HealthKind.SYMPTOM -> {
                excerpts.appendLine("· 症状记录 ${list.size} 条：")
                list.take(5).forEach { record ->
                    excerpts.appendLine(
                        "  ${DateUtils.monthDay(record.dateIso)} ${record.timeLabel} " +
                            record.note.ifBlank { "未填写" }
                    )
                }
            }
        }
    }
    if (!hasMetric) excerpts.appendLine("· 近 30 天暂无记录")
    excerpts.appendLine()

    var sectionNo = 1
    fun header(text: String) {
        sectionNo += 1
        val numeral = when (sectionNo) {
            2 -> "二"
            3 -> "三"
            4 -> "四"
            5 -> "五"
            else -> "${sectionNo}"
        }
        excerpts.appendLine("$numeral、$text")
    }

    val abnormal = recent.filter { it.abnormal }.take(6)
    if (abnormal.isNotEmpty()) {
        header("需要关注")
        abnormal.forEach { record ->
            val valueText = if (record.kind == HealthKind.BP) {
                "${record.systolic}/${record.diastolic}"
            } else {
                "%.1f".format(record.value)
            }
            excerpts.appendLine(
                "· ${DateUtils.monthDay(record.dateIso)} ${record.timeLabel} " +
                    "${record.kind.label} $valueText ${record.kind.unit}（${record.abnormalLabel}）"
            )
        }
        excerpts.appendLine()
    }

    header("当前用药")
    val minePlans = plans.filter { member == null || it.memberId == member.id }
    val active = minePlans.filter { it.activeOn(today) }
    if (active.isEmpty()) {
        excerpts.appendLine("· 暂无在服计划")
    } else {
        active.sortedBy { it.startDate }.forEach { plan ->
            excerpts.appendLine(
                "· ${plan.medicineName} ${plan.dose}，每天 ${plan.timeLabel}" +
                    "（${DateUtils.monthDay(plan.startDate)} 开始，共 ${plan.days} 天）"
            )
        }
    }
    excerpts.appendLine()

    val changed = minePlans.filter { plan ->
        DateUtils.parseIso(plan.startDate)?.let { !it.isBefore(from) } ?: false
    }
    if (changed.isNotEmpty()) {
        header("近 30 天新增或调整")
        changed.sortedBy { it.startDate }.forEach { plan ->
            excerpts.appendLine(
                "· ${DateUtils.monthDay(plan.startDate)} 起：${plan.medicineName} ${plan.dose}，每天 ${plan.timeLabel}"
            )
        }
        excerpts.appendLine()
    }

    val notes = recent.filter { it.note.isNotBlank() && it.kind != HealthKind.SYMPTOM }
    if (notes.isNotEmpty()) {
        header("备注")
        notes.take(6).forEach { record ->
            excerpts.appendLine("· ${DateUtils.monthDay(record.dateIso)} ${record.note}")
        }
        excerpts.appendLine()
    }

    excerpts.append("以上内容由「药忆」根据本机记录自动汇总，仅供就诊沟通参考，不作为诊断依据。")
    return excerpts.toString()
}
