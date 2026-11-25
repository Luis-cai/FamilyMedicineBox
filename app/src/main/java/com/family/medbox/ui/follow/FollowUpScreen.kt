package com.family.medbox.ui.follow

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.family.medbox.data.model.Member
import com.family.medbox.data.prefs.FollowUp
import com.family.medbox.ui.components.MemberAvatar
import com.family.medbox.ui.components.Pill
import com.family.medbox.ui.components.PrimaryButton
import com.family.medbox.ui.components.SelectableChip
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
import com.family.medbox.ui.theme.Outline
import com.family.medbox.ui.theme.Primary
import com.family.medbox.ui.theme.PrimaryContainer
import com.family.medbox.ui.theme.SurfaceWhite
import com.family.medbox.util.DateUtils
import java.time.LocalDate
import java.util.UUID

/**
 * 复诊 · 续方：把「什么时候再去医院」「处方还能开到哪天」放在一屏，
 * 快到期时提醒带好药单、医保卡和就诊摘要。
 */
@Composable
fun FollowUpScreen(
    members: List<Member>,
    items: List<FollowUp>,
    onSave: (FollowUp) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit
) {
    var editing by remember { mutableStateOf<FollowUp?>(null) }
    var adding by remember { mutableStateOf(false) }

    val upcoming = items.sortedBy { it.visitIso.ifBlank { "9999-12-31" } }
    val nextVisit = upcoming.firstOrNull { (it.daysToVisit ?: Long.MAX_VALUE) >= 0L }

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
                        text = "复诊 · 续方",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink
                    )
                    Text(
                        text = "提前准备，别断药",
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
                    Text("新增", style = MaterialTheme.typography.labelMedium, color = Primary)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 14.dp)
        ) {
            if (upcoming.isEmpty()) {
                SoftCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    padding = 18.dp
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
                                Icons.Rounded.EventAvailable,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "还没有复诊安排",
                                style = MaterialTheme.typography.titleSmall,
                                color = Ink
                            )
                            Text(
                                text = "记下下次复诊日，提前 3 天提醒带齐材料",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(text = "添加一条", onClick = { adding = true })
                }
                return@Column
            }

            nextVisit?.let { next ->
                SoftCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    padding = 16.dp,
                    background = PrimaryContainer
                ) {
                    Text(
                        text = "下一次复诊",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = DateUtils.displayDate(next.visitIso),
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = next.visitLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = Primary
                        )
                    }
                    if (next.hospital.isNotBlank() || next.department.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = listOf(next.memberName, next.hospital, next.department)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            items.forEach { item ->
                FollowUpCard(
                    item = item,
                    onClick = { editing = item },
                    onDelete = { onDelete(item.id) }
                )
            }

            Text(
                text = "复诊前 3 天准备好：医保卡、既往药单、最近的指标记录和就诊摘要。",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
            )
        }
    }

    if (adding || editing != null) {
        FollowUpDialog(
            members = members,
            existing = editing,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { item ->
                onSave(item)
                adding = false
                editing = null
            }
        )
    }
}

@Composable
private fun FollowUpCard(
    item: FollowUp,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val member = Member(
        id = item.memberId,
        name = item.memberName.ifBlank { "家庭成员" },
        colorIndex = (item.memberId % 6).toInt()
    )
    val overdue = (item.daysToVisit ?: 0L) < 0L
    val urgent = (item.daysToVisit ?: Long.MAX_VALUE) in 0L..3L

    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        padding = 14.dp,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(text = member.avatarText, colorIndex = member.colorIndex, size = 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.memberName.ifBlank { "家庭成员" },
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink
                )
                Text(
                    text = listOf(item.hospital, item.department)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { "医院 / 科室未填写" },
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (urgent) {
                Pill(
                    text = item.visitLabel,
                    container = AccentContainer,
                    content = AccentInk
                )
            } else if (overdue) {
                Pill(text = item.visitLabel, container = DangerContainer, content = Danger)
            } else {
                Pill(text = item.visitLabel, container = PrimaryContainer, content = Primary)
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

        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Divider)
        )
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateCell(
                label = "复诊日",
                value = if (item.visitIso.isBlank()) "未填写" else DateUtils.displayCompact(item.visitIso),
                hint = item.visitIso.takeIf { it.isNotBlank() }?.let { weekday(it) }.orEmpty(),
                modifier = Modifier.weight(1f)
            )
            DateCell(
                label = "处方续方",
                value = if (item.prescriptionIso.isBlank()) {
                    "未填写"
                } else {
                    DateUtils.displayCompact(item.prescriptionIso)
                },
                hint = when (val days = item.daysToPrescription) {
                    null -> "可留空"
                    else -> when {
                        days < 0L -> "已过期 ${-days} 天"
                        days == 0L -> "今天到期"
                        else -> "还能开 $days 天"
                    }
                },
                warn = (item.daysToPrescription ?: Long.MAX_VALUE) <= 7L,
                modifier = Modifier.weight(1f)
            )
        }

        if (item.needsPrep) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentContainer)
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = AccentInk,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "该准备了：医保卡 · 药单 · 就诊摘要",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentInk
                )
            }
        }

        if (item.note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.note,
                style = MaterialTheme.typography.labelSmall,
                color = InkSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DateCell(
    label: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
    warn: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Background)
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = InkMuted)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (hint.isNotBlank()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = if (warn) AccentInk else InkMuted
            )
        }
    }
}

@Composable
private fun FollowUpDialog(
    members: List<Member>,
    existing: FollowUp?,
    onDismiss: () -> Unit,
    onSave: (FollowUp) -> Unit
) {
    var memberId by remember {
        mutableStateOf(existing?.memberId ?: members.firstOrNull()?.id ?: 0L)
    }
    var hospital by remember { mutableStateOf(existing?.hospital.orEmpty()) }
    var department by remember { mutableStateOf(existing?.department.orEmpty()) }
    var visitIso by remember { mutableStateOf(existing?.visitIso.orEmpty()) }
    var prescriptionIso by remember { mutableStateOf(existing?.prescriptionIso.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    val member = members.firstOrNull { it.id == memberId }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = SurfaceWhite) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = if (existing == null) "新增复诊安排" else "编辑复诊安排",
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "只保存在本机，随时可以改",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )

                Spacer(Modifier.height(14.dp))
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
                OutlinedTextField(
                    value = hospital,
                    onValueChange = { hospital = it },
                    label = { Text("医院（可选）") },
                    placeholder = { Text("如：市第一人民医院") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text("科室（可选）") },
                    placeholder = { Text("如：心内科") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))
                DateField(label = "复诊日", iso = visitIso, onChange = { visitIso = it })
                Spacer(Modifier.height(10.dp))
                DateField(
                    label = "处方可开到（可选）",
                    iso = prescriptionIso,
                    onChange = { prescriptionIso = it }
                )

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    placeholder = { Text("如：空腹抽血，早上别吃早饭") },
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

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
                        enabled = member != null && visitIso.isNotBlank(),
                        onClick = {
                            val m = member ?: return@PrimaryButton
                            onSave(
                                FollowUp(
                                    id = existing?.id ?: UUID.randomUUID().toString(),
                                    memberId = m.id,
                                    memberName = m.name,
                                    hospital = hospital.trim(),
                                    department = department.trim(),
                                    visitIso = visitIso,
                                    prescriptionIso = prescriptionIso,
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
private fun DateField(label: String, iso: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val parsed = DateUtils.parseIso(iso) ?: DateUtils.today()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceWhite)
            .border(1.dp, Outline, RoundedCornerShape(14.dp))
            .clickable {
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        onChange(DateUtils.toIso(LocalDate.of(year, month + 1, day)))
                    },
                    parsed.year,
                    parsed.monthValue - 1,
                    parsed.dayOfMonth
                ).show()
            }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = InkMuted)
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (iso.isBlank()) "选择日期" else DateUtils.displayDate(iso),
                style = MaterialTheme.typography.bodyLarge,
                color = if (iso.isBlank()) InkMuted else Ink
            )
        }
        Icon(
            Icons.Rounded.CalendarMonth,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(20.dp)
        )
    }
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

private fun weekday(iso: String): String {
    val date = DateUtils.parseIso(iso) ?: return ""
    return when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
}
