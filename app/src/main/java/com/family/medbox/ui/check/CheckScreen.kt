﻿﻿﻿package com.family.medbox.ui.check

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.family.medbox.AppContainer
import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.Member
import com.family.medbox.domain.AgentBrain
import com.family.medbox.domain.CheckResult
import com.family.medbox.domain.CheckVerdict
import com.family.medbox.domain.InteractionRules
import com.family.medbox.domain.InteractionSeverity
import com.family.medbox.domain.InteractionWarning
import com.family.medbox.ui.components.AppSearchField
import com.family.medbox.ui.components.ExpiryBadge
import com.family.medbox.ui.components.Pill
import com.family.medbox.ui.components.ScreenHeader
import com.family.medbox.ui.components.StepTrace
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
import com.family.medbox.ui.theme.Success
import com.family.medbox.ui.theme.SuccessContainer
import com.family.medbox.ui.theme.SurfaceWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 购药查重：掏钱之前的最后一道家庭药品记忆校验。
 */
@Composable
fun CheckScreen(
    container: AppContainer,
    medicines: List<Medicine>,
    members: List<Member>,
    onOpenMedicine: (Medicine) -> Unit,
    onAddWithName: (String) -> Unit,
    onAskAgent: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingCapture by remember { mutableStateOf(false) }
    var photoPath by remember { mutableStateOf<String?>(null) }

    val result: CheckResult? = remember(query, medicines) {
        if (query.isBlank()) null else container.shoppingGuard.check(query, medicines)
    }

    // 和问药页一样，把「识别 → 匹配 → 比对」的推理过程摆出来
    val steps: List<AgentBrain.Step> = remember(result, medicines.size) {
        result?.let { checked ->
            buildList {
                add(AgentBrain.Step("识别查询", checked.query))
                checked.ingredient?.takeIf { it.isNotBlank() }
                    ?.let { add(AgentBrain.Step("匹配成分", it)) }
                add(
                    AgentBrain.Step(
                        "比对家中库存",
                        "${medicines.size} 种 · 命中 ${checked.matched.size} 盒"
                    )
                )
            }
        }.orEmpty()
    }

    val interactions = remember(medicines) { InteractionRules.check(medicines) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val path = photoPath
            if (path != null) {
                scope.launch {
                    scanning = true
                    notice = null
                    try {
                        val text = withContext(Dispatchers.IO) {
                            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                            val bitmap = BitmapFactory.decodeFile(path, options)
                            bitmap?.let { container.ocr.recognize(it) }.orEmpty()
                        }
                        val parsed = container.textParser.parse(text)
                        if (parsed.name.isNotBlank() || parsed.ingredient.isNotBlank()) {
                            query = parsed.name.ifBlank { parsed.ingredient }
                            notice = "已识别为「${query}」，下面是查重结果"
                        } else {
                            notice = "没能识别出药名，可以手动输入试试"
                        }
                    } catch (t: Throwable) {
                        notice = "识别失败，请手动输入药名"
                    } finally {
                        scanning = false
                    }
                }
            }
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) pendingCapture = true else notice = "需要相机权限才能拍照识别" }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                scanning = true
                notice = null
                try {
                    val parsed = withContext(Dispatchers.IO) {
                        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, options)
                        }
                        val text = bitmap?.let { container.ocr.recognize(it) }.orEmpty()
                        bitmap?.recycle()
                        container.textParser.parse(text)
                    }
                    if (parsed.name.isNotBlank() || parsed.ingredient.isNotBlank()) {
                        query = parsed.name.ifBlank { parsed.ingredient }
                        notice = "已识别为「$query」，下面是查重结果"
                    } else {
                        notice = "没能识别出药名，可以换张图或手动输入"
                    }
                } catch (t: Throwable) {
                    notice = "识别失败，请手动输入药名"
                } finally {
                    scanning = false
                }
            }
        }
    }

    LaunchedEffect(pendingCapture) {
        if (pendingCapture) {
            pendingCapture = false
            val dir = File(context.cacheDir, "captures").apply { mkdirs() }
            val file = File(dir, "check_${System.currentTimeMillis()}.jpg")
            photoPath = file.absolutePath
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            takePicture.launch(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to HeroTint,
                    0.3f to Background,
                    1f to Background
                )
            )
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp)
    ) {
        ScreenHeader(
            eyebrow = "购药前先查一查",
            title = "要不要买",
            subtitle = "输入商品名，比对家里是否已有同成分的药。"
        )

        Spacer(Modifier.height(2.dp))

        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "输入药名或上传药盒",
                leadingIcon = Icons.Rounded.Search,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceWhite)
                    .border(1.dp, Outline, RoundedCornerShape(16.dp))
                    .clickable(enabled = !scanning) {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) pendingCapture = true
                        else cameraPermission.launch(Manifest.permission.CAMERA)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Primary
                    )
                } else {
                    Icon(
                        Icons.Rounded.PhotoCamera,
                        contentDescription = "拍药盒识别",
                        tint = Primary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceWhite)
                    .border(1.dp, Outline, RoundedCornerShape(16.dp))
                    .clickable(enabled = !scanning) { pickImage.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Primary
                    )
                } else {
                    Icon(
                        Icons.Rounded.PhotoLibrary,
                        contentDescription = "从相册上传图片识别",
                        tint = Primary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        Text(
            text = "常用查询",
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.dp,
                vertical = 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(4) { index ->
                val sample = listOf("感康", "布洛芬", "999 感冒灵", "蒙脱石散")[index]
                Surface(
                    modifier = Modifier.clickable { query = sample },
                    shape = RoundedCornerShape(50),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, Divider)
                ) {
                    Text(
                        text = sample,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSecondary
                    )
                }
            }
        }

        if (notice != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryContainer)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = notice.orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = Primary
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { notice = null },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "关闭提示",
                        tint = Primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (interactions.isNotEmpty()) {
            Text(
                text = "用药安全 · 药箱内相互作用",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(8.dp))
            interactions.take(3).forEach { warning ->
                InteractionCard(warning)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))
        }

        if (result == null) {
            if (interactions.isEmpty()) {
                Text(
                    text = "输入药名或拍照识别，立刻比对家中库存",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                )
            }
        } else {
            if (steps.isNotEmpty()) {
                StepTrace(
                    steps = steps,
                    limit = 3,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
                Spacer(Modifier.height(2.dp))
            }
            VerdictCard(
                result = result,
                members = members,
                onOpenMedicine = onOpenMedicine,
                onAddWithName = { onAddWithName(result.query) },
                onAddToShopping = {
                    container.shoppingStore.addAll(
                        names = listOf(result.query),
                        reason = "查重后准备补货"
                    )
                },
                onAskAgent = { onAskAgent("${result.query}和家里的药能一起吃吗") }
            )
        }
    }
}

@Composable
private fun VerdictCard(
    result: CheckResult,
    members: List<Member>,
    onOpenMedicine: (Medicine) -> Unit,
    onAddWithName: () -> Unit,
    onAddToShopping: () -> Unit,
    onAskAgent: () -> Unit
) {
    var addedToShopping by rememberSaveable(result.query) { mutableStateOf(false) }
    val (icon, container, accent, label) = when (result.verdict) {
        CheckVerdict.ENOUGH -> VerdictStyle(
            Icons.Rounded.CheckCircle, SuccessContainer, Success, "建议不用买"
        )
        CheckVerdict.LOW -> VerdictStyle(
            Icons.Rounded.Info, AccentContainer, AccentInk, "按需补充"
        )
        CheckVerdict.SAME_INGREDIENT_RISK -> VerdictStyle(
            Icons.Rounded.Error, AccentContainer, AccentInk, "成分重复"
        )
        CheckVerdict.ONLY_EXPIRED -> VerdictStyle(
            Icons.Rounded.Error, DangerContainer, Danger, "已有但已过期"
        )
        CheckVerdict.NOT_FOUND -> VerdictStyle(
            Icons.Rounded.CheckCircle, PrimaryContainer, Primary, "可以购买"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceWhite)
            .border(1.dp, Divider, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(container),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.headline,
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "查询：${result.query}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted
                )
            }
            Pill(text = label, container = container, content = accent)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = result.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = InkSecondary,
            lineHeight = 22.sp
        )

        if (result.matched.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "家中库存",
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted
            )
            Spacer(Modifier.height(6.dp))
            result.matched.forEach { medicine ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Background)
                        .clickable { onOpenMedicine(medicine) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = medicine.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = Ink,
                            maxLines = 1
                        )
                        val owner = members.firstOrNull { it.id == medicine.memberId }?.name
                        val meta = listOfNotNull(
                            medicine.stockLabel,
                            medicine.location.takeIf { it.isNotBlank() },
                            owner
                        ).joinToString(" · ")
                        Text(
                            text = meta,
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

        if (!result.safetyNote.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentContainer)
                    .padding(10.dp)
            ) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = AccentInk,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = result.safetyNote.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7A5510),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultAction(
                label = if (addedToShopping) "已加入购物清单" else "加入购物清单",
                primary = !addedToShopping,
                done = addedToShopping,
                onClick = {
                    addedToShopping = true
                    onAddToShopping()
                }
            )
            ResultAction(label = "问药师", onClick = onAskAgent)
        }

        if (result.verdict == CheckVerdict.NOT_FOUND || result.verdict == CheckVerdict.ONLY_EXPIRED) {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onAddWithName),
                shape = RoundedCornerShape(12.dp),
                color = Primary
            ) {
                Text(
                    text = "买回来后，直接录入药箱",
                    modifier = Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = OnPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ResultAction(
    label: String,
    primary: Boolean = false,
    done: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = when {
        primary -> Primary
        done -> SuccessContainer
        else -> SurfaceWhite
    }
    val contentColor = when {
        primary -> OnPrimary
        done -> Success
        else -> Primary
    }
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(enabled = !done, onClick = onClick),
        shape = RoundedCornerShape(50),
        color = containerColor,
        border = if (primary || done) null else BorderStroke(1.dp, Outline)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
private fun InteractionCard(warning: InteractionWarning) {
    val severe = warning.severity == InteractionSeverity.HIGH
    val accent = if (severe) Danger else AccentInk
    val container = if (severe) DangerContainer else AccentContainer
    var expanded by rememberSaveable(warning.title) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceWhite)
            .border(1.dp, Divider, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = warning.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                Pill(
                    text = if (severe) "重点注意" else "需留意",
                    container = container,
                    content = accent
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = warning.medicineNames.joinToString(" + "),
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = warning.detail,
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                lineHeight = 18.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(container.copy(alpha = 0.45f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "怎么处理",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = adviceFor(warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    // 视觉小巧，但热区撑到 44dp
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = if (expanded) "收起" else "为什么",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
        }
    }
}

/** 针对常见相互作用给一句能照做的处理建议。 */
private fun adviceFor(warning: InteractionWarning): String = when {
    warning.title.contains("退烧") || warning.title.contains("重复") ->
        "先只保留一种含相同成分的药，复方感冒药和中成药不要叠加；需要退烧时按说明书间隔服用，24 小时不超过 4 次。"
    warning.title.contains("出血") ->
        "两者尽量错开服用；需要止痛时先问医师，避免长期同服。"
    else ->
        "拿不准时，把药名、剂量和正在吃的其他药一起发给医师或药师确认。"
}

private data class VerdictStyle(
    val icon: ImageVector,
    val container: Color,
    val accent: Color,
    val label: String
)
