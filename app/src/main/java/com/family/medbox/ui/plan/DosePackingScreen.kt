package com.family.medbox.ui.plan

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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.model.Member
import com.family.medbox.ui.components.Pill
import com.family.medbox.ui.components.PrimaryButton
import com.family.medbox.ui.components.SoftCard
import com.family.medbox.ui.theme.Accent
import com.family.medbox.ui.theme.AccentContainer
import com.family.medbox.ui.theme.AccentInk
import com.family.medbox.ui.theme.Background
import com.family.medbox.ui.theme.Divider
import com.family.medbox.ui.theme.Ink
import com.family.medbox.ui.theme.InkMuted
import com.family.medbox.ui.theme.InkSecondary
import com.family.medbox.ui.theme.MemberPalette
import com.family.medbox.ui.theme.OnPrimary
import com.family.medbox.ui.theme.Outline
import com.family.medbox.ui.theme.Primary
import com.family.medbox.ui.theme.PrimaryContainer
import com.family.medbox.ui.theme.Success
import com.family.medbox.ui.theme.SuccessContainer
import com.family.medbox.ui.theme.SurfaceWhite
import com.family.medbox.util.DateUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private data class PackedDose(val plan: MedicationPlan, val time: String)

/**
 * 每周分药助手：把本周每个时间点的药按「早 / 中 / 下午 / 晚 / 睡前」排成清单，
 * 分完一天就在日历上打个勾，下周再从头来。
 */
@Composable
fun DosePackingScreen(
    plans: List<MedicationPlan>,
    members: List<Member>,
    packedDays: Set<String>,
    onTogglePacked: (String) -> Unit,
    onClose: () -> Unit
) {
    val today = DateUtils.today()
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    var weekStart by remember { mutableStateOf(thisMonday) }
    var selected by remember { mutableStateOf(today) }

    val days = remember(weekStart) { (0L..6L).map { weekStart.plusDays(it) } }
    val dosesByDay = remember(plans, weekStart) {
        days.associateWith { date ->
            plans.filter { it.activeOn(date) }
                .flatMap { plan -> plan.times.map { PackedDose(plan, it) } }
                .sortedBy { it.time }
        }
    }
    val daysWithDoses = days.filter { dosesByDay[it].orEmpty().isNotEmpty() }
    val packedCount = daysWithDoses.count { DateUtils.toIso(it) in packedDays }

    fun shiftWeek(weeks: Long) {
        val nextStart = weekStart.plusWeeks(weeks)
        val offset = (selected.dayOfWeek.value - 1).toLong()
        weekStart = nextStart
        selected = nextStart.plusDays(offset)
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
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Ink)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "本周分药",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink
                    )
                    Text(
                        text = "按格子分好，一周照着吃",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }
                Pill(
                    text = "$packedCount/${daysWithDoses.size} 天已分",
                    container = if (packedCount == daysWithDoses.size && daysWithDoses.isNotEmpty()) {
                        SuccessContainer
                    } else {
                        PrimaryContainer
                    },
                    content = if (packedCount == daysWithDoses.size && daysWithDoses.isNotEmpty()) {
                        Success
                    } else {
                        Primary
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            WeekCard(
                weekStart = weekStart,
                days = days,
                selected = selected,
                today = today,
                packedDays = packedDays,
                daysWithDoses = daysWithDoses,
                onPrev = { shiftWeek(-1) },
                onNext = { shiftWeek(1) },
                onSelect = { selected = it },
                onBackToThisWeek = {
                    weekStart = thisMonday
                    selected = today
                }
            )

            Spacer(Modifier.height(12.dp))

            val selectedIso = DateUtils.toIso(selected)
            val doses = dosesByDay[selected].orEmpty()
            val packed = selectedIso in packedDays

            DayDetailCard(
                date = selected,
                today = today,
                doses = doses,
                members = members,
                packed = packed
            )

            Spacer(Modifier.height(12.dp))

            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 14.dp,
                background = AccentContainer
            ) {
                Row {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = AccentInk,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "分装提示：先分长期药，再分疗程药；胰岛素等需要冷藏的药不要提前放进药盒。分完在本页打个勾，漏分装时一眼就能看出来。",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentInk,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        val selectedIso = DateUtils.toIso(selected)
        val selectedDoses = dosesByDay[selected].orEmpty()
        val packed = selectedIso in packedDays
        Surface(color = SurfaceWhite, shadowElevation = 10.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                PrimaryButton(
                    text = when {
                        selectedDoses.isEmpty() -> "这一天没有需要分装的药"
                        packed -> "撤销「${DateUtils.monthDay(selectedIso)}」的分装标记"
                        else -> "标记「${DateUtils.monthDay(selectedIso)}」已分装"
                    },
                    enabled = selectedDoses.isNotEmpty(),
                    onClick = { onTogglePacked(selectedIso) }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "只记录分装进度，不影响打卡和库存",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WeekCard(
    weekStart: LocalDate,
    days: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    packedDays: Set<String>,
    daysWithDoses: List<LocalDate>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onBackToThisWeek: () -> Unit
) {
    val packedCount = daysWithDoses.count { DateUtils.toIso(it) in packedDays }
    val weekDone = daysWithDoses.isNotEmpty() && packedCount == daysWithDoses.size
    SoftCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "上一周", tint = InkSecondary)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (weekStart) {
                        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) -> "本周"
                        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1) -> "下周"
                        else -> "一周安排"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary
                )
                Text(
                    text = "${DateUtils.monthDay(DateUtils.toIso(weekStart))} - " +
                        DateUtils.monthDay(DateUtils.toIso(weekStart.plusDays(6))),
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "下一周", tint = InkSecondary)
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                DayCell(
                    date = day,
                    selected = day == selected,
                    isToday = day == today,
                    packed = DateUtils.toIso(day) in packedDays,
                    hasDoses = day in daysWithDoses,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(day) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (daysWithDoses.isEmpty()) {
                    "本周还没有服药计划"
                } else {
                    "本周 $packedCount/${daysWithDoses.size} 天已分装"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = if (weekDone) Success else InkMuted
            )
            if (weekStart != today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) {
                Text(
                    text = "回到本周",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onBackToThisWeek)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(Divider)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(
                        if (daysWithDoses.isEmpty()) 0f else packedCount.toFloat() / daysWithDoses.size
                    )
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(if (weekDone) Success else Primary)
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    packed: Boolean,
    hasDoses: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val circleColor = when {
        selected -> Primary
        packed -> SuccessContainer
        else -> Color.Transparent
    }
    val numberColor = when {
        selected -> OnPrimary
        packed -> Success
        hasDoses -> Ink
        else -> InkMuted
    }
    val labelColor = when {
        selected -> Primary
        isToday -> Primary
        else -> InkMuted
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = weekdayShort(date.dayOfWeek),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(circleColor)
                .then(
                    if (isToday && !selected) {
                        Modifier.border(1.2.dp, Primary, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                color = numberColor
            )
        }
        Spacer(Modifier.height(3.dp))
        if (packed) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "已分装",
                tint = if (selected) Primary else Success,
                modifier = Modifier.size(13.dp)
            )
        } else {
            Spacer(Modifier.height(13.dp))
        }
    }
}

@Composable
private fun DayDetailCard(
    date: LocalDate,
    today: LocalDate,
    doses: List<PackedDose>,
    members: List<Member>,
    packed: Boolean
) {
    SoftCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dayTitle(date, today),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Text(
                    text = if (doses.isEmpty()) {
                        "没有需要分装的药"
                    } else {
                        "${doses.size} 次 · 共 ${doses.map { it.plan.medicineName }.distinct().size} 种药"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
            }
            if (packed) {
                Pill(text = "已分装", container = SuccessContainer, content = Success)
            }
        }

        if (doses.isEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Background)
                    .padding(horizontal = 13.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Medication,
                    contentDescription = null,
                    tint = InkMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = if (today == date) {
                        "今天没有服药计划。去首页「添加计划」后，这里会自动排好。"
                    } else {
                        "这一天没有服药计划，不用分装。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                    lineHeight = 18.sp
                )
            }
            return@SoftCard
        }

        doses.groupBy { slotLabel(it.time) }.forEach { (label, slotDoses) ->
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${slotDoses.size} 次",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Background)
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                slotDoses.forEachIndexed { index, dose ->
                    if (index > 0) HorizontalDivider(color = Divider)
                    DoseLine(dose = dose, members = members)
                }
            }
        }
    }
}

@Composable
private fun DoseLine(dose: PackedDose, members: List<Member>) {
    val member = members.firstOrNull { it.id == dose.plan.memberId }
    val dotColor = member?.let {
        MemberPalette[it.colorIndex % MemberPalette.size]
    } ?: Primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dose.plan.medicineName,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink
            )
            Text(
                text = listOf(
                    dose.plan.dose,
                    member?.name ?: dose.plan.memberName.ifBlank { "全家" }
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = dose.time,
            style = MaterialTheme.typography.labelLarge,
            color = InkSecondary
        )
    }
}

private fun slotLabel(time: String): String {
    val hour = time.substringBefore(':').toIntOrNull() ?: 8
    return when {
        hour < 10 -> "早上"
        hour < 14 -> "中午"
        hour < 18 -> "下午"
        hour < 22 -> "晚上"
        else -> "睡前"
    }
}

private fun weekdayShort(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

private fun weekdayCn(day: DayOfWeek): String = "周" + weekdayShort(day)

private fun dayTitle(date: LocalDate, today: LocalDate): String {
    val prefix = when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        today.minusDays(1) -> "昨天"
        else -> ""
    }
    return listOf(
        prefix,
        DateUtils.monthDay(DateUtils.toIso(date)),
        weekdayCn(date.dayOfWeek)
    ).filter { it.isNotBlank() }.joinToString(" ")
}
