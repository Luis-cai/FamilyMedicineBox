package com.family.medbox.ui.agent

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.family.medbox.AppContainer
import com.family.medbox.data.model.Medicine
import com.family.medbox.domain.AgentBrain
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
import com.family.medbox.util.DateUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long = System.nanoTime(),
    val fromUser: Boolean,
    val text: String,
    val medicines: List<Medicine> = emptyList(),
    val tone: AgentBrain.Tone = AgentBrain.Tone.NORMAL,
    val suggestions: List<String> = emptyList(),
    /** 本轮调用了哪些药箱工具，按顺序展示成推理轨迹 */
    val steps: List<AgentBrain.Step> = emptyList(),
    /** 回答后可直接执行的下一步动作 */
    val actions: List<AgentBrain.Action> = emptyList(),
    /** 需要用户澄清的候选（如多个同成分药品） */
    val candidates: List<String> = emptyList(),
    val time: String = DateUtils.chatTimeStamp()
)

@Composable
fun AgentScreen(
    container: AppContainer,
    messages: List<ChatMessage>,
    lastTopic: String? = null,
    onAsk: (String) -> Unit,
    onOpenMedicine: (Medicine) -> Unit,
    onAction: (AgentBrain.Action) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    // 对话开始后可以退回「13 组工具」首页，再点继续回来，聊天记录不丢。
    var showTools by rememberSaveable { mutableStateOf(false) }

    /** 从工具页或输入框发起提问时，自动切回对话视图。 */
    fun ask(text: String) {
        showTools = false
        onAsk(text)
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            listening = true
            container.voiceInput.start(
                onResult = { text ->
                    listening = false
                    ask(text)
                },
                onError = { message ->
                    listening = false
                    notice = message
                }
            )
        } else {
            notice = "需要麦克风权限才能语音提问"
        }
    }

    fun submit() {
        val text = input.trim()
        if (text.isEmpty()) return
        ask(text)
        input = ""
    }

    val chatVisible = messages.isNotEmpty() && !showTools
    BackHandler(enabled = chatVisible) { showTools = true }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // 退回工具页时回到顶部，避免沿用对话时的滚动位置。
    LaunchedEffect(showTools) {
        if (showTools) listState.scrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding()
    ) {
        // 顶部：工具页显示身份介绍，对话中显示带返回按钮的紧凑标题栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(HeroTint, Background)
                    )
                )
                .statusBarsPadding()
        ) {
            if (chatVisible) {
                AgentChatHeader(onBack = { showTools = true })
            } else {
                ScreenHeader(
                    eyebrow = "AI Agent · 端侧推理 · 不联网",
                    title = "家庭药师",
                    subtitle = "13 组工具 + 守门员 · 教练 · 出行 · 采购",
                    trailing = {
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.SmartToy,
                                    contentDescription = null,
                                    tint = OnPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Success)
                                    .border(2.dp, SurfaceWhite, CircleShape)
                            )
                        }
                    }
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() || showTools) {
                item {
                    AgentIntro(
                        lastTopic = lastTopic,
                        hasHistory = messages.isNotEmpty(),
                        onResume = { showTools = false },
                        onAsk = { ask(it) }
                    )
                }
            }
            if (!showTools) {
                itemsIndexed(messages) { index, message ->
                    if (message.fromUser) {
                        UserBubble(message)
                    } else {
                        AgentBubble(
                            message = message,
                            animate = index == messages.lastIndex,
                            onAsk = { ask(it) },
                            onOpenMedicine = onOpenMedicine,
                            onAction = onAction
                        )
                    }
                }
                val lastSuggestions = messages.lastOrNull()?.takeIf { !it.fromUser }?.suggestions
                if (!lastSuggestions.isNullOrEmpty()) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(lastSuggestions) { suggestion ->
                                SuggestionChip(text = suggestion) { ask(suggestion) }
                            }
                        }
                    }
                }
            }
        }

        if (notice != null) {
            Text(
                text = notice.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Danger
            )
        }

        // 输入栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, Outline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        if (input.isEmpty()) {
                            Text(
                                "问库存、有效期，或两种药能否同服",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkMuted,
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSend = { submit() }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (listening) PrimaryContainer else Color.Transparent)
                            .clickable {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    listening = true
                                    container.voiceInput.start(
                                        onResult = { text ->
                                            listening = false
                                            ask(text)
                                        },
                                        onError = { message ->
                                            listening = false
                                            notice = message
                                        }
                                    )
                                } else {
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = "语音提问",
                            tint = if (listening) Primary else InkMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Primary)
                    .clickable { submit() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Send,
                    contentDescription = "发送",
                    tint = OnPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** 对话中的紧凑标题栏：左上角可返回 13 组工具页，聊天记录保留。 */
@Composable
private fun AgentChatHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(SurfaceWhite)
                .border(1.dp, Divider, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回工具页",
                tint = Ink,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "家庭药师",
                style = MaterialTheme.typography.titleMedium,
                color = Ink
            )
            Text(
                text = "13 组工具 · 在线",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted
            )
        }
        Box {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.SmartToy,
                    contentDescription = null,
                    tint = OnPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(Success)
                    .border(2.dp, SurfaceWhite, CircleShape)
            )
        }
    }
}

/** 首页空态：用任务卡代替示例问题，引导用户直接调用工具。 */
@Composable
private fun AgentIntro(
    lastTopic: String?,
    hasHistory: Boolean,
    onResume: () -> Unit,
    onAsk: (String) -> Unit
) {
    Column(modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)) {
        Text(
            text = "不只是聊天：每次都会先查药箱、核对库存和有效期，再给你可以直接点的下一步。",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSecondary,
            lineHeight = 21.sp
        )
        if (hasHistory) {
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onResume),
                shape = RoundedCornerShape(14.dp),
                color = PrimaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.SmartToy,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "继续刚才的对话",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = Primary
                    )
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = Primary
                    )
                }
            }
        }
        if (!lastTopic.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAsk(lastTopic) },
                shape = RoundedCornerShape(14.dp),
                color = AccentContainer.copy(alpha = 0.55f)
            ) {
                Text(
                    text = "接着上次问：「$lastTopic」",
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentInk,
                    maxLines = 2
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "让我做点什么",
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted
        )
        Spacer(Modifier.height(8.dp))
        val capabilities = listOf(
            Capability("库存盘点", "有多少种、各剩多少", Icons.Rounded.Inventory2, "家里一共有多少种药？"),
            Capability("临期清理", "该先吃还是该扔", Icons.Rounded.Schedule, "哪些药快过期了？"),
            Capability("用药冲突", "两种药能否同服", Icons.Rounded.WarningAmber, "泰诺和感冒灵能一起吃吗？"),
            Capability("今日服药", "几点吃、吃了没", Icons.Rounded.Medication, "今天还有哪些药没吃？"),
            Capability("出行药包", "出差几天带什么", Icons.Rounded.Luggage, "下周出差 3 天带什么药"),
            Capability("采购清单", "该补什么、一键核销", Icons.Rounded.ShoppingCart, "该买什么药")
        )
        CapabilityCard(
            item = Capability("家庭守门员", "全家的药会不会冲突、今天谁没打卡", Icons.Rounded.HealthAndSafety, "全家用药安全巡检"),
            modifier = Modifier.fillMaxWidth(),
            onClick = { onAsk("全家用药安全巡检") }
        )
        Spacer(Modifier.height(8.dp))
        CapabilityCard(
            item = Capability("依从性教练", "最近总漏哪个时段、怎么调", Icons.Rounded.Insights, "最近我按时吃药了吗"),
            modifier = Modifier.fillMaxWidth(),
            onClick = { onAsk("最近我按时吃药了吗") }
        )
        Spacer(Modifier.height(8.dp))
        CapabilityCard(
            item = Capability("药箱体检", "过期、缺药、打卡一次看完", Icons.Rounded.MonitorHeart, "帮我做个药箱体检"),
            modifier = Modifier.fillMaxWidth(),
            onClick = { onAsk("帮我做个药箱体检") }
        )
        Spacer(Modifier.height(8.dp))
        CapabilityCard(
            item = Capability("一句话建计划", "「布洛芬早晚各一粒，吃 5 天」", Icons.Rounded.AutoAwesome, "把布洛芬设成早晚各一粒，吃 5 天"),
            modifier = Modifier.fillMaxWidth(),
            onClick = { onAsk("把布洛芬设成早晚各一粒，吃 5 天") }
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            capabilities.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { item ->
                        CapabilityCard(
                            item = item,
                            modifier = Modifier.weight(1f),
                            onClick = { onAsk(item.question) }
                        )
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "也可以直接输入，或点麦克风说一句，比如「布洛芬还剩多少」。",
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            lineHeight = 18.sp
        )
    }
}

private data class Capability(
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val question: String
)

@Composable
private fun CapabilityCard(
    item: Capability,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, Divider)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = PrimaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Primary
        )
    }
}

@Composable
private fun UserBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
            color = Primary
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = OnPrimary
            )
        }
    }
}

@Composable
private fun AgentBubble(
    message: ChatMessage,
    animate: Boolean,
    onAsk: (String) -> Unit,
    onOpenMedicine: (Medicine) -> Unit,
    onAction: (AgentBrain.Action) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(PrimaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.SmartToy,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Surface(
                shape = RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, Divider)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.tone != AgentBrain.Tone.NORMAL) {
                        val (container, content, label) = when (message.tone) {
                            AgentBrain.Tone.DANGER -> Triple(DangerContainer, Danger, "用药风险")
                            AgentBrain.Tone.WARN -> Triple(AccentContainer, AccentInk, "需要注意")
                            AgentBrain.Tone.GOOD -> Triple(SuccessContainer, Success, "可以放心")
                            else -> Triple(PrimaryContainer, Primary, "提示")
                        }
                        Pill(text = label, container = container, content = content)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (message.steps.isNotEmpty()) {
                        StepTrace(message.steps)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Divider)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    StreamingText(
                        text = message.text,
                        animate = animate
                    )
                }
            }
            if (message.medicines.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                message.medicines.take(4).forEach { medicine ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { onOpenMedicine(medicine) },
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, Divider)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = medicine.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Ink,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${medicine.stockLabel} · 成分 ${medicine.ingredient.ifBlank { "未记录" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted,
                                    maxLines = 1
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            ExpiryBadge(medicine)
                        }
                    }
                }
            }
            if (message.candidates.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(message.candidates) { candidate ->
                        SuggestionChip(text = candidate) { onAsk(candidate) }
                    }
                }
            }
            if (message.actions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    message.actions.take(2).forEachIndexed { index, action ->
                        ActionChip(
                            action = action,
                            primary = index == 0,
                            onClick = { onAction(action) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = message.time,
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun ActionChip(
    action: AgentBrain.Action,
    primary: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (primary) Primary else SurfaceWhite,
        border = if (primary) null else BorderStroke(1.dp, Outline)
    ) {
        Text(
            text = action.label,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (primary) OnPrimary else Primary
        )
    }
}

/** 文字逐字出现，模拟智能体边推理边输出。 */
@Composable
private fun StreamingText(text: String, animate: Boolean) {
    var visible by remember(text) { mutableStateOf(if (animate) 0 else text.length) }
    LaunchedEffect(text, animate) {
        if (!animate) {
            visible = text.length
            return@LaunchedEffect
        }
        while (visible < text.length) {
            delay(16)
            visible = (visible + 3).coerceAtMost(text.length)
        }
    }
    Text(
        text = text.take(visible),
        style = MaterialTheme.typography.bodyMedium,
        color = Ink,
        lineHeight = 22.sp
    )
}
