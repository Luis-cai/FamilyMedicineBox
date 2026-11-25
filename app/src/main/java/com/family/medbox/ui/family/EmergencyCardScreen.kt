package com.family.medbox.ui.family

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.model.Member
import com.family.medbox.data.prefs.EmergencyInfo
import com.family.medbox.ui.components.MemberAvatar
import com.family.medbox.ui.components.Pill
import com.family.medbox.ui.components.PrimaryButton
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
import com.family.medbox.ui.theme.SurfaceWhite
import com.family.medbox.util.DateUtils

/**
 * 家庭急救卡：一屏出示过敏史、慢病、当前用药和紧急联系人，
 * 信息只保存在本机，可编辑、可拨号、可分享文字版给家人或医生。
 */
@Composable
fun EmergencyCardScreen(
    members: List<Member>,
    plans: List<MedicationPlan>,
    infos: Map<Long, EmergencyInfo>,
    onSave: (Long, EmergencyInfo) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val today = DateUtils.today()
    var selectedId by remember { mutableStateOf(members.firstOrNull()?.id ?: 0L) }
    var editing by remember { mutableStateOf(false) }
    val member = members.firstOrNull { it.id == selectedId } ?: members.firstOrNull()
    val info = member?.let { infos[it.id] } ?: EmergencyInfo()
    val activePlans = remember(plans, member?.id) {
        member?.let { m -> plans.filter { it.memberId == m.id && it.activeOn(today) } }.orEmpty()
    }

    fun cardText(): String = buildString {
        appendLine("【家庭急救卡】${member?.name.orEmpty()}")
        if (member != null) appendLine("关系：${member.relation}")
        if (info.bloodType.isNotBlank()) appendLine("血型：${info.bloodType}")
        appendLine("过敏：${info.allergies.ifBlank { "未填写" }}")
        appendLine("慢病：${info.conditions.ifBlank { "未填写" }}")
        appendLine(
            "当前用药：" + activePlans.joinToString("；") { plan ->
                "${plan.medicineName} ${plan.dose}".trim()
            }.ifBlank { "无" }
        )
        appendLine(
            "紧急联系人：${info.contactName.ifBlank { "未填写" }} ${info.contactPhone}".trim()
        )
        if (info.note.isNotBlank()) appendLine("备注：${info.note}")
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
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = Ink
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "家庭急救卡",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink
                    )
                    Text(
                        text = "过敏 · 慢病 · 当前用药 · 紧急联系人",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }
                if (member != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(PrimaryContainer)
                            .clickable { editing = true }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "编辑",
                            style = MaterialTheme.typography.labelMedium,
                            color = Primary
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            if (members.isEmpty()) {
                SoftCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                    Text(
                        text = "还没有家庭成员。先在家庭页添加成员，再填写急救信息。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
                return@Column
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                members.forEach { m ->
                    val selected = m.id == member?.id
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { selectedId = m.id },
                        shape = RoundedCornerShape(50),
                        color = if (selected) PrimaryContainer else SurfaceWhite,
                        border = BorderStroke(1.dp, if (selected) Primary.copy(alpha = 0.4f) else Divider)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MemberAvatar(
                                text = m.avatarText,
                                colorIndex = m.colorIndex,
                                size = 24.dp,
                                selected = selected
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = m.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) Primary else InkSecondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (member != null) {
                SoftCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MemberAvatar(
                            text = member.avatarText,
                            colorIndex = member.colorIndex,
                            size = 46.dp,
                            selected = true
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = member.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = Ink
                            )
                            Text(
                                text = listOf(
                                    member.relation,
                                    if (activePlans.isEmpty()) null else "${activePlans.size} 项在用"
                                ).filterNotNull().joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted
                            )
                        }
                        if (info.bloodType.isNotBlank()) {
                            Pill(
                                text = info.bloodType,
                                container = DangerContainer,
                                content = Danger
                            )
                        }
                    }

                    if (info.isBlank) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "还没有填写过敏史和紧急联系人。点右上角「编辑」补全，急救时才能第一时间用上。",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(AccentContainer)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentInk,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    FieldBlock(
                        icon = Icons.Rounded.Warning,
                        iconTint = Danger,
                        label = "过敏史",
                        value = info.allergies.ifBlank { "未填写" },
                        valueColor = if (info.allergies.isBlank()) InkMuted else Ink,
                        background = DangerContainer
                    )

                    Spacer(Modifier.height(10.dp))
                    FieldBlock(
                        icon = Icons.Rounded.Favorite,
                        iconTint = AccentInk,
                        label = "慢病 / 长期病",
                        value = info.conditions.ifBlank { "未填写" },
                        valueColor = if (info.conditions.isBlank()) InkMuted else Ink,
                        background = AccentContainer
                    )

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Medication,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "当前用药",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkSecondary
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "按今天的计划",
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
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        if (activePlans.isEmpty()) {
                            Text(
                                text = "今天没有服药计划",
                                modifier = Modifier.padding(vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted
                            )
                        } else {
                            activePlans.forEachIndexed { index, plan ->
                                if (index > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Divider)
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = plan.medicineName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = plan.dose,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = InkMuted
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = plan.times.joinToString(" / "),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = InkSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Phone,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "紧急联系人",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkSecondary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Background)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = info.contactName.ifBlank { "未填写" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (info.contactName.isBlank()) InkMuted else Ink
                            )
                            if (info.contactPhone.isNotBlank()) {
                                Text(
                                    text = info.contactPhone,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Primary
                                )
                            }
                        }
                        if (info.contactPhone.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Primary)
                                    .clickable {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_DIAL,
                                                    Uri.parse("tel:${info.contactPhone}")
                                                )
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Phone,
                                    contentDescription = "拨打电话",
                                    tint = SurfaceWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (info.note.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "备注",
                            style = MaterialTheme.typography.labelMedium,
                            color = InkMuted
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = info.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary,
                            lineHeight = 19.sp
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "仅保存在本机，不上传家庭组；就诊或急救时直接出示这一页。",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        if (member != null) {
            Surface(color = SurfaceWhite, shadowElevation = 10.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    PrimaryButton(
                        text = if (info.contactPhone.isBlank()) "填写紧急联系人" else "拨打紧急联系人",
                        onClick = {
                            if (info.contactPhone.isBlank()) {
                                editing = true
                            } else {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.parse("tel:${info.contactPhone}")
                                        )
                                    )
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { editing = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("编辑信息")
                        }
                        OutlinedButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, cardText())
                                }
                                context.startActivity(
                                    Intent.createChooser(send, "分享急救卡")
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("分享卡片")
                        }
                    }
                }
            }
        }
    }

    if (editing && member != null) {
        EmergencyEditDialog(
            member = member,
            initial = info,
            onDismiss = { editing = false },
            onSave = {
                onSave(member.id, it)
                editing = false
            }
        )
    }
}

@Composable
private fun FieldBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    background: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = iconTint
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = if (value == "未填写") FontWeight.Normal else FontWeight.Medium,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun EmergencyEditDialog(
    member: Member,
    initial: EmergencyInfo,
    onDismiss: () -> Unit,
    onSave: (EmergencyInfo) -> Unit
) {
    var allergies by remember { mutableStateOf(initial.allergies) }
    var conditions by remember { mutableStateOf(initial.conditions) }
    var bloodType by remember { mutableStateOf(initial.bloodType) }
    var contactName by remember { mutableStateOf(initial.contactName) }
    var contactPhone by remember { mutableStateOf(initial.contactPhone) }
    var note by remember { mutableStateOf(initial.note) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceWhite,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = "编辑急救信息",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Text(
                    text = "${member.name} · ${member.relation}",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )

                Spacer(Modifier.height(14.dp))
                EmergencyField(
                    label = "过敏史",
                    value = allergies,
                    placeholder = "如：青霉素、磺胺类、海鲜",
                    onChange = { allergies = it },
                    minLines = 2
                )
                EmergencyField(
                    label = "慢病 / 长期病",
                    value = conditions,
                    placeholder = "如：高血压、2 型糖尿病",
                    onChange = { conditions = it }
                )
                EmergencyField(
                    label = "血型",
                    value = bloodType,
                    placeholder = "如：A 型 Rh+",
                    onChange = { bloodType = it },
                    singleLine = true
                )
                EmergencyField(
                    label = "紧急联系人",
                    value = contactName,
                    placeholder = "姓名或称呼",
                    onChange = { contactName = it },
                    singleLine = true
                )
                EmergencyField(
                    label = "联系人电话",
                    value = contactPhone,
                    placeholder = "手机号",
                    onChange = { contactPhone = it },
                    singleLine = true,
                    keyboardType = KeyboardType.Phone
                )
                EmergencyField(
                    label = "备注",
                    value = note,
                    placeholder = "如：对某类药过敏的处理方式、就医偏好",
                    onChange = { note = it },
                    minLines = 2
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消", color = InkMuted)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1.4f)) {
                        PrimaryButton(
                            text = "保存",
                            onClick = {
                                onSave(
                                    EmergencyInfo(
                                        allergies = allergies.trim(),
                                        conditions = conditions.trim(),
                                        bloodType = bloodType.trim(),
                                        contactName = contactName.trim(),
                                        contactPhone = contactPhone.trim(),
                                        note = note.trim()
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmergencyField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        label = { Text(label) },
        placeholder = { Text(placeholder, color = InkMuted) },
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}
