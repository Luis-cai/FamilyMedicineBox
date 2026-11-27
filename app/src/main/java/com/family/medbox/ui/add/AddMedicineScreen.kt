package com.family.medbox.ui.add

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.family.medbox.AppContainer
import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.Member
import com.family.medbox.domain.ocr.MedicineTextParser
import com.family.medbox.ui.components.ChoicePill
import com.family.medbox.ui.components.MemberAvatar
import com.family.medbox.ui.components.PrimaryButton
import com.family.medbox.ui.components.SoftCard
import com.family.medbox.ui.theme.AccentContainer
import com.family.medbox.ui.theme.Background
import com.family.medbox.ui.theme.Danger
import com.family.medbox.ui.theme.Divider
import com.family.medbox.ui.theme.Ink
import com.family.medbox.ui.theme.InkMuted
import com.family.medbox.ui.theme.OnPrimary
import com.family.medbox.ui.theme.Outline
import com.family.medbox.ui.theme.Primary
import com.family.medbox.ui.theme.PrimaryContainer
import com.family.medbox.ui.theme.SurfaceWhite
import com.family.medbox.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * 药品录入页：拍照识别 / 语音录入 / 手动填写 三种方式共用一份表单。
 */
@Composable
fun AddMedicineScreen(
    container: AppContainer,
    members: List<Member>,
    editing: Medicine?,
    onClose: () -> Unit,
    onOpenStarterKit: () -> Unit,
    onSave: (Medicine) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val form = remember(editing?.id) { MedicineFormState(editing) }

    var photoPath by remember { mutableStateOf<String?>(null) }
    var pendingCapture by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var rawText by remember { mutableStateOf("") }
    var showExpiryPicker by remember { mutableStateOf(false) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val path = photoPath
            if (path != null) {
                scope.launch {
                    scanning = true
                    error = null
                    try {
                        val bitmap = withContext(Dispatchers.IO) { decodeSampled(path) }
                        if (bitmap == null) {
                            error = "图片读取失败，请重试或手动填写"
                        } else {
                            val text = container.ocr.recognize(bitmap)
                            rawText = text
                            val parsed = container.textParser.parse(text)
                            if (parsed.isUseful || parsed.expiryDate != null) {
                                form.fill(parsed)
                                hint = "已从药盒识别出 ${buildList {
                                    if (parsed.name.isNotBlank()) add("名称")
                                    if (parsed.ingredient.isNotBlank()) add("成分")
                                    if (parsed.spec.isNotBlank()) add("规格")
                                    if (parsed.expiryDate != null) add("有效期")
                                }.joinToString("、")}，请核对后保存"
                            } else {
                                hint = "没能识别出关键信息，可以再拍一张更清晰的照片"
                            }
                        }
                    } catch (t: Throwable) {
                        error = "识别失败：${t.message ?: "未知错误"}"
                    } finally {
                        scanning = false
                        photoPath = null
                        // 识别完成即删除临时照片，不让药盒照片长期占用手机空间
                        withContext(Dispatchers.IO) {
                            runCatching { File(path).delete() }
                        }
                    }
                }
            }
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingCapture = true else error = "需要相机权限才能拍照识别药盒"
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            listening = true
            container.voiceInput.start(
                onResult = { text ->
                    listening = false
                    val parsed = container.textParser.parse(text)
                    if (parsed.isUseful || parsed.expiryDate != null) {
                        form.fill(parsed)
                        hint = "已从「$text」中提取信息，请核对"
                    } else {
                        form.name = text.take(30)
                        hint = "已把听到的内容填入名称，请核对"
                    }
                },
                onError = { message ->
                    listening = false
                    error = message
                }
            )
        } else {
            error = "需要麦克风权限才能语音录入"
        }
    }

    LaunchedEffect(pendingCapture) {
        if (pendingCapture) {
            pendingCapture = false
            val dir = File(context.cacheDir, "captures").apply { mkdirs() }
            val file = File(dir, "medicine_${System.currentTimeMillis()}.jpg")
            photoPath = file.absolutePath
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            takePicture.launch(uri)
        }
    }

    fun submit() {
        val err = form.validate()
        if (err != null) {
            error = err
        } else {
            onSave(form.toMedicine(editing))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding()
    ) {
        // 顶部栏
        Surface(color = SurfaceWhite) {
            Column(modifier = Modifier.statusBarsPadding()) {
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
                            text = if (editing != null && editing.id != 0L) "编辑药品" else "录入药品",
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink
                        )
                        Text(
                            text = "拍照最快，识别结果可以修改",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                }
                HorizontalDivider(color = Divider)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            SoftCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                SectionLabel("快速录入", "拍药盒或说一句，自动填好表单")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CaptureButton(
                    icon = Icons.Rounded.PhotoCamera,
                    title = "拍药盒",
                    subtitle = "端侧识别",
                    busy = scanning,
                    modifier = Modifier.weight(1f)
                ) {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) pendingCapture = true
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                }
                CaptureButton(
                    icon = Icons.Rounded.Mic,
                    title = if (listening) "正在听…" else "说一句",
                    subtitle = "语音录入",
                    busy = listening,
                    modifier = Modifier.weight(1f)
                ) {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        listening = true
                        container.voiceInput.start(
                            onResult = { text ->
                                listening = false
                                val parsed = container.textParser.parse(text)
                                if (parsed.isUseful) {
                                    form.fill(parsed)
                                    hint = "已从「$text」中提取信息，请核对"
                                } else {
                                    form.name = text.take(30)
                                    hint = "已把听到的内容填入名称，请核对"
                                }
                            },
                            onError = { message ->
                                listening = false
                                error = message
                            }
                        )
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryContainer)
                        .clickable(onClick = onOpenStarterKit)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.PlaylistAdd,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "用家庭常备药模板",
                            style = MaterialTheme.typography.labelLarge,
                            color = Primary
                        )
                        Text(
                            text = "一次勾选多种常见药，有效期待补",
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary.copy(alpha = 0.72f)
                        )
                    }
                    Text(
                        text = "去勾选 ›",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary
                    )
                }
            }

            if (hint != null) {
                Spacer(Modifier.height(10.dp))
                InfoBanner(
                    text = hint.orEmpty(),
                    container = PrimaryContainer,
                    content = Primary
                )
            }
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                InfoBanner(
                    text = error.orEmpty(),
                    container = com.family.medbox.ui.theme.DangerContainer,
                    content = Danger,
                    onClose = { error = null }
                )
            }
            if (rawText.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "识别原文：${rawText.take(120)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted
                )
            }

            Spacer(Modifier.height(12.dp))
            SoftCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                SectionLabel("基本信息", "带 * 为必填，其余可稍后补充")
                Spacer(Modifier.height(8.dp))

            LabeledField(
                label = "药品名称 *",
                value = form.name,
                onValueChange = { form.name = it },
                placeholder = "如：芬必得 布洛芬缓释胶囊"
            )
            LabeledField(
                label = "主要成分",
                value = form.ingredient,
                onValueChange = { form.ingredient = it },
                placeholder = "如：布洛芬（用于同成分查重）"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledField(
                    label = "规格",
                    value = form.spec,
                    onValueChange = { form.spec = it },
                    placeholder = "0.3g×20粒",
                    modifier = Modifier.weight(1f)
                )
                LabeledField(
                    label = "剂型",
                    value = form.form,
                    onValueChange = { form.form = it },
                    placeholder = "胶囊",
                    modifier = Modifier.weight(1f)
                )
            }
            }

            Spacer(Modifier.height(12.dp))
            SoftCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                SectionLabel("库存与归属", "过期提醒与成员分类依赖这几项")
                Spacer(Modifier.height(8.dp))

            LabeledField(
                label = "数量",
                value = form.quantity,
                onValueChange = { input ->
                    form.quantity = input.filter { it.isDigit() }.take(4)
                },
                placeholder = "0",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth()
            )
            Column {
                Text(
                    text = "单位",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(form.units.size) { index ->
                        val unit = form.units[index]
                        ChoicePill(
                            label = unit,
                            selected = form.unit == unit,
                            onClick = { form.unit = unit }
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = "有效期至 *",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Background,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showExpiryPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (form.expiry.isBlank()) "点击选择日期"
                            else DateUtils.displayDate(form.expiry),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (form.expiry.isBlank()) InkMuted else Ink,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (form.expiry.isBlank()) "选择" else "修改",
                            style = MaterialTheme.typography.labelMedium,
                            color = Primary
                        )
                    }
                }
            }

            Text(
                text = "归属成员",
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(members.size) { index ->
                    val member = members[index]
                    val selected = form.memberId == member.id
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) Primary else SurfaceWhite)
                            .border(
                                1.dp,
                                if (selected) Primary else Divider,
                                RoundedCornerShape(50)
                            )
                            .clickable {
                                form.memberId = if (selected) null else member.id
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemberAvatar(
                            text = member.name,
                            colorIndex = member.colorIndex,
                            size = 20.dp,
                            selected = selected
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = member.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) OnPrimary else Ink
                        )
                    }
                }
            }
            }

            Spacer(Modifier.height(12.dp))

            SoftCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                SectionLabel("更多信息", "存放位置与备注，可稍后补充")
                Spacer(Modifier.height(8.dp))
                LabeledField(
                    label = "存放位置",
                    value = form.location,
                    onValueChange = { form.location = it },
                    placeholder = "如：客厅药箱"
                )
                LabeledField(
                    label = "备注",
                    value = form.note,
                    onValueChange = { form.note = it },
                    placeholder = "用法用量、注意事项等",
                    singleLine = false
                )
            }

            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AccentContainer
            ) {
                Text(
                    text = "录入后可用于查重、过期提醒与问药，数据只存本机。",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7A5510),
                    lineHeight = 18.sp
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        Surface(color = SurfaceWhite, shadowElevation = 12.dp) {
            Column {
                HorizontalDivider(color = Divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "必填：药品名称、有效期",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        text = "保存到药箱",
                        onClick = { submit() },
                        modifier = Modifier.width(148.dp)
                    )
                }
            }
        }
    }

    if (showExpiryPicker) {
        ExpiryMonthPicker(
            initial = form.expiry,
            onDismiss = { showExpiryPicker = false },
            onConfirm = {
                form.expiry = it
                showExpiryPicker = false
            }
        )
    }
}

@Composable
private fun SectionLabel(title: String, hint: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(2.dp))
        Text(hint, style = MaterialTheme.typography.labelSmall, color = InkMuted)
    }
}

@Composable
private fun CaptureButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Background)
            .border(1.dp, Divider, RoundedCornerShape(16.dp))
            .clickable(enabled = !busy, onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Primary
                    )
                } else {
                    Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.labelLarge, color = Ink)
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = InkMuted)
            }
        }
    }
}

@Composable
private fun InfoBanner(
    text: String,
    container: Color,
    content: Color,
    onClose: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = content,
            lineHeight = 18.sp
        )
        if (onClose != null) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onClose() }
            )
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: String? = null
) {
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = InkMuted)
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Background,
            border = androidx.compose.foundation.BorderStroke(1.dp, Divider)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = InkMuted
                        )
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        readOnly = readOnly,
                        singleLine = singleLine,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (trailing != null) {
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary
                    )
                }
            }
        }
    }
}

/** 简单可靠的年月选择器（药品有效期通常只精确到月） */
@Composable
private fun ExpiryMonthPicker(
    initial: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val today = DateUtils.today()
    val initialDate = DateUtils.parseIso(initial) ?: today.plusYears(1)
    var year by remember { mutableStateOf(initialDate.year) }
    var month by remember { mutableStateOf(initialDate.monthValue) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = SurfaceWhite) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("选择有效期", style = MaterialTheme.typography.titleLarge, color = Ink)
                Spacer(Modifier.height(14.dp))
                Text("年份", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(6) { index ->
                        val y = today.year + index
                        ChoicePill(
                            label = "$y",
                            selected = year == y,
                            onClick = { year = y }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("月份", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..6).forEach { m ->
                        MonthPill(
                            label = "$m",
                            selected = month == m,
                            onClick = { month = m },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (7..12).forEach { m ->
                        MonthPill(
                            label = "$m",
                            selected = month == m,
                            onClick = { month = m },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消", color = InkMuted)
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val endOfMonth = LocalDate.of(year, month, 1)
                                    .plusMonths(1).minusDays(1)
                                onConfirm(DateUtils.toIso(endOfMonth))
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = Primary
                    ) {
                        Text(
                            text = "确定",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 11.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = OnPrimary,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** 月份用等宽紧凑胶囊，避免两位数换行 */
@Composable
private fun MonthPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) Primary else SurfaceWhite
    val fg = if (selected) OnPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = bg,
        border = if (selected) null else BorderStroke(1.dp, Outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

private class MedicineFormState(initial: Medicine?) {

    val units = listOf("粒", "片", "袋", "瓶", "支", "份")

    var name by mutableStateOf(initial?.name.orEmpty())
    var ingredient by mutableStateOf(initial?.ingredient.orEmpty())
    var spec by mutableStateOf(initial?.spec.orEmpty())
    var form by mutableStateOf(initial?.form.orEmpty())
    var quantity by mutableStateOf(initial?.quantity?.takeIf { it > 0 }?.toString().orEmpty())
    var unit by mutableStateOf(initial?.unit ?: "粒")
    var expiry by mutableStateOf(initial?.expiryDate.orEmpty())
    var memberId by mutableStateOf(initial?.memberId)
    var location by mutableStateOf(initial?.location.orEmpty())
    var note by mutableStateOf(initial?.note.orEmpty())

    fun fill(parsed: MedicineTextParser.Parsed) {
        if (parsed.name.isNotBlank()) name = parsed.name
        if (parsed.ingredient.isNotBlank()) ingredient = parsed.ingredient
        if (parsed.spec.isNotBlank()) spec = parsed.spec
        if (parsed.form.isNotBlank()) form = parsed.form
        if (parsed.quantity > 0) {
            quantity = parsed.quantity.toString()
            if (parsed.unit in units) unit = parsed.unit
        }
        parsed.expiryDate?.let { expiry = it }
    }

    fun validate(): String? = when {
        name.isBlank() -> "请填写药品名称"
        expiry.isBlank() -> "请选择有效期，过期提醒依赖这个日期"
        else -> null
    }

    fun toMedicine(editing: Medicine?): Medicine = Medicine(
        id = editing?.id ?: 0L,
        name = name.trim(),
        ingredient = ingredient.trim(),
        category = editing?.category ?: "",
        spec = spec.trim(),
        form = form.trim(),
        quantity = quantity.toIntOrNull() ?: 1,
        unit = unit,
        expiryDate = expiry.ifBlank { null },
        memberId = memberId,
        location = location.trim(),
        note = note.trim(),
        createdAt = editing?.createdAt ?: System.currentTimeMillis()
    )
}

private fun decodeSampled(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1600) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, options)
}
