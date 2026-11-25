﻿﻿﻿package com.family.medbox.ui.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.Member
import com.family.medbox.ui.components.ChoicePill
import com.family.medbox.ui.components.PrimaryButton
import com.family.medbox.ui.theme.Background
import com.family.medbox.ui.theme.Divider
import com.family.medbox.ui.theme.Ink
import com.family.medbox.ui.theme.InkMuted
import com.family.medbox.ui.theme.InkSecondary
import com.family.medbox.ui.theme.Outline
import com.family.medbox.ui.theme.Primary
import com.family.medbox.ui.theme.PrimaryContainer
import com.family.medbox.ui.theme.SurfaceWhite

/**
 * 添加服药计划：药名 + 剂量 + 成员 + 每日时间点 + 疗程天数。
 * 保存后由 DoseReminderScheduler 排本地闹钟。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DosePlanSheet(
    members: List<Member>,
    existingPlans: List<MedicationPlan>,
    medicines: List<Medicine> = emptyList(),
    initialMedicineName: String? = null,
    initialDose: String = "",
    initialTimes: List<String> = emptyList(),
    initialDays: Int = 7,
    onDismiss: () -> Unit,
    onDelete: (MedicationPlan) -> Unit,
    onSave: (name: String, dose: String, member: Member?, times: List<String>, days: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initialMedicineName.orEmpty()) }
    var dose by remember { mutableStateOf(initialDose) }
    var memberId by remember { mutableStateOf<Long?>(null) }
    val times = remember {
        mutableStateListOf<String>().apply {
            addAll(initialTimes.ifEmpty { listOf("08:00") })
        }
    }
    var days by remember { mutableStateOf(initialDays) }

    val canSave = name.isNotBlank() && times.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceWhite,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Outline) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "添加服药计划",
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "到点本地提醒，吃过后在首页点一下打卡。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted
                )
            }
            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                if (medicines.isNotEmpty()) {
                    Text(
                        text = "从药箱选（可选）",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        medicines.forEach { medicine ->
                            ChoicePill(
                                label = medicine.name,
                                selected = name == medicine.name,
                                onClick = {
                                    name = medicine.name
                                    if (dose.isBlank()) dose = "1 ${medicine.unit}"
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                PlanField(
                    label = "药品名称",
                    value = name,
                    placeholder = "如：芬必得 布洛芬缓释胶囊",
                    onValueChange = { name = it }
                )
                PlanField(
                    label = "每次剂量",
                    value = dose,
                    placeholder = "如：1 粒 / 5ml",
                    onValueChange = { dose = it }
                )

                Text(
                    text = "服药成员",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted
                )
                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    ChoicePill(
                        label = "不指定",
                        selected = memberId == null,
                        onClick = { memberId = null }
                    )
                    members.forEach { member ->
                        ChoicePill(
                            label = member.name,
                            selected = memberId == member.id,
                            onClick = {
                                memberId = if (memberId == member.id) null else member.id
                            }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "每天服药时间（可多选）",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted
                )
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("08:00", "12:00", "18:00", "21:00").forEach { time ->
                        ChoicePill(
                            label = time,
                            selected = time in times,
                            onClick = {
                                if (time in times) times.remove(time) else times.add(time)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "疗程天数",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted
                )
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(3, 7, 14, 30).forEach { option ->
                        ChoicePill(
                            label = "$option 天",
                            selected = days == option,
                            onClick = { days = option }
                        )
                    }
                }

                if (existingPlans.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "已有计划",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    existingPlans.forEach { plan ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Background)
                                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = plan.medicineName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Ink,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${plan.timeLabel} · ${plan.days} 天 · ${plan.dose}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onDelete(plan) },
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
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (canSave) "到点会弹本地通知" else "填写药名并选时间",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = "保存计划",
                    modifier = Modifier.width(132.dp),
                    enabled = canSave,
                    onClick = {
                        val member = members.firstOrNull { it.id == memberId }
                        onSave(
                            name.trim(),
                            dose.trim().ifBlank { "1 次用量" },
                            member,
                            times.sorted(),
                            days
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun PlanField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = InkMuted)
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Background,
            border = BorderStroke(1.dp, Divider),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkMuted
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
