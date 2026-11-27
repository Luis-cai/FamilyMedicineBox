package com.family.medbox.ui.sync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.medbox.data.sync.SyncInfo
import com.family.medbox.ui.components.StatusDot
import com.family.medbox.ui.theme.Accent
import com.family.medbox.ui.theme.Background
import com.family.medbox.ui.theme.Danger
import com.family.medbox.ui.theme.Divider
import com.family.medbox.ui.theme.Ink
import com.family.medbox.ui.theme.InkMuted
import com.family.medbox.ui.theme.InkSecondary
import com.family.medbox.ui.theme.OnPrimary
import com.family.medbox.ui.theme.Outline
import com.family.medbox.ui.theme.Primary
import com.family.medbox.ui.theme.PrimaryContainer
import com.family.medbox.ui.theme.Success
import com.family.medbox.ui.theme.SurfaceWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSheet(
    info: SyncInfo,
    pending: Int,
    busy: Boolean,
    message: String,
    messageIsError: Boolean,
    onDismiss: () -> Unit,
    onLogin: (serverUrl: String, nickname: String, phone: String) -> Unit,
    onCreateFamily: (String) -> Unit,
    onJoinFamily: (String) -> Unit,
    onSync: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(info.serverUrl) }
    var nickname by remember { mutableStateOf(info.nickname) }
    var phone by remember { mutableStateOf(info.phone) }
    var familyName by remember { mutableStateOf("我的家庭") }
    var inviteCode by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceWhite,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
        ) {
            Text("家庭同步", style = MaterialTheme.typography.titleLarge, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "把服药计划和打卡同步到家庭组，家人的手机也能收到提醒。" +
                    "药箱清单、照片和语音不上传。",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(14.dp))
            if (!info.loggedIn) {
                SyncField(
                    label = "服务器地址",
                    value = server,
                    onValueChange = { server = it },
                    placeholder = SyncInfo.DEFAULT_SERVER
                )
                Text(
                    text = "模拟器默认 127.0.0.1:8000，需先执行 adb reverse tcp:8000 tcp:8000；真机填电脑局域网 IP。",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
                Spacer(Modifier.height(6.dp))
                SyncField(
                    label = "昵称",
                    value = nickname,
                    onValueChange = { nickname = it },
                    placeholder = "如：爸爸"
                )
                SyncField(
                    label = "手机号",
                    value = phone,
                    onValueChange = { phone = it },
                    placeholder = "用于区分家人，演示号即可"
                )
                Spacer(Modifier.height(10.dp))
                PrimaryAction(
                    text = if (busy) "正在连接…" else "登录 / 注册",
                    enabled = !busy && nickname.isNotBlank(),
                    onClick = { onLogin(server, nickname.trim(), phone.trim()) }
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            info.nickname.take(1).ifBlank { "我" },
                            color = Primary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info.nickname,
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink
                        )
                        Text(
                            text = if (info.inFamily) {
                                "家庭组「${info.familyName}」· 邀请码 ${info.inviteCode}"
                            } else {
                                "还没有加入家庭组"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                if (info.inFamily) {
                    val lastSync = info.lastSyncAt.take(16).replace('T', ' ')
                        .takeIf { it.isNotBlank() }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(
                            color = when {
                                !info.enabled -> InkMuted
                                pending > 0 -> Accent
                                else -> Success
                            },
                            size = 6.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = buildString {
                                append(
                                    when {
                                        !info.enabled -> "自动同步已关闭"
                                        pending > 0 -> "$pending 项改动待上传"
                                        lastSync == null -> "尚未完成首次同步"
                                        else -> "计划都已同步"
                                    }
                                )
                                if (lastSync != null) append(" · 上次 $lastSync")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = InkMuted
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Background)
                            .clickable {
                                runCatching {
                                    val clipboard = context.getSystemService(
                                        Context.CLIPBOARD_SERVICE
                                    ) as? ClipboardManager
                                    clipboard?.setPrimaryClip(
                                        ClipData.newPlainText("家庭邀请码", info.inviteCode)
                                    )
                                    copied = true
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "家庭邀请码",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted
                            )
                            Text(
                                text = info.inviteCode,
                                style = MaterialTheme.typography.titleMedium,
                                color = Ink,
                                letterSpacing = 3.sp
                            )
                        }
                        Text(
                            text = if (copied) "已复制" else "复制",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (copied) Success else Primary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自动同步",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ink
                            )
                            Text(
                                text = "新增或删除计划时自动上传",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted
                            )
                        }
                        Switch(
                            checked = info.enabled,
                            onCheckedChange = onToggleEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnPrimary,
                                checkedTrackColor = Primary
                            )
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryAction(
                        text = if (busy) "正在同步…" else "立即同步",
                        enabled = !busy,
                        onClick = onSync
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("退出家庭同步", color = InkMuted)
                    }
                } else {
                    SyncField(
                        label = "家庭组名称",
                        value = familyName,
                        onValueChange = { familyName = it },
                        placeholder = "我的家庭"
                    )
                    PrimaryAction(
                        text = if (busy) "正在创建…" else "创建家庭组",
                        enabled = !busy && familyName.isNotBlank(),
                        onClick = { onCreateFamily(familyName.trim()) }
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(Divider)
                        )
                        Text(
                            text = "  或者加入家人的家庭组  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(Divider)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SyncField(
                        label = "邀请码",
                        value = inviteCode,
                        onValueChange = { inviteCode = it.uppercase() },
                        placeholder = "6 位字母数字"
                    )
                    PrimaryAction(
                        text = if (busy) "正在加入…" else "加入家庭组",
                        enabled = !busy && inviteCode.length >= 4,
                        onClick = { onJoinFamily(inviteCode.trim()) },
                        filled = false
                    )
                }
            }

            if (message.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (messageIsError) Danger else Primary,
                    lineHeight = 18.sp
                )
            }

            if (busy) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "家庭组只保存药名、用量、时间和打卡状态；" +
                    "同步失败不会影响本机提醒。",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun PrimaryAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    filled: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    !filled -> PrimaryContainer
                    enabled -> Primary
                    else -> Primary.copy(alpha = 0.35f)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) OnPrimary else Primary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SyncField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkMuted)
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Background)
                .padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
