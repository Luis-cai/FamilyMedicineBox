﻿﻿﻿package com.family.medbox.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.StockStatus
import com.family.medbox.ui.theme.Accent
import com.family.medbox.ui.theme.AccentContainer
import com.family.medbox.ui.theme.AccentInk
import com.family.medbox.ui.theme.Danger
import com.family.medbox.ui.theme.DangerContainer
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

/**
 * 页面通用头部：小标签 + 大标题 + 说明，统一所有 Tab 的起手式。
 */
@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Primary)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary,
                    letterSpacing = 1.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    letterSpacing = (-0.4).sp
                ),
                color = Ink
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun MemberAvatar(
    text: String,
    colorIndex: Int,
    size: Dp = 32.dp,
    selected: Boolean = false
) {
    val color = MemberPalette[colorIndex.mod(MemberPalette.size)]
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) color else color.copy(alpha = 0.12f))
            .then(
                if (selected) Modifier.border(2.dp, color.copy(alpha = 0.35f), CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.take(1),
            color = if (selected) OnPrimary else color,
            fontSize = (size.value * 0.38f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 可选中胶囊：支持自定义主色与左侧内容（头像 / 圆点），用于筛选与单位选择。
 */
@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Primary,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            // 视觉保持紧凑，热区撑到 44dp，方便老人和单手操作
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else SurfaceWhite)
            .border(1.dp, if (selected) accent else Outline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) OnPrimary else InkSecondary,
            maxLines = 1
        )
    }
}

@Composable
fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = SelectableChip(
    label = label,
    selected = selected,
    onClick = onClick,
    modifier = modifier
)

@Composable
fun Pill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = container) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = content
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    trailingInline: Boolean = false,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = Ink)
        if (trailing != null && trailingInline) {
            // 紧随标题的小字，避免右侧信息被悬浮按钮遮挡
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
                modifier = if (onTrailingClick != null) Modifier.clickable { onTrailingClick() } else Modifier
            )
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    modifier = if (onTrailingClick != null) Modifier.clickable { onTrailingClick() } else Modifier
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    elevation: Dp = 0.dp,
    background: Color = SurfaceWhite,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = background,
        shadowElevation = elevation,
        border = if (elevation > 0.dp) null else BorderStroke(1.dp, Divider)
    ) {
        Column(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(padding),
            content = content
        )
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    background: Color,
    size: Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3f))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun ExpiryBadge(medicine: Medicine) {
    val (container, content) = when (medicine.status) {
        StockStatus.EXPIRED -> DangerContainer to Danger
        StockStatus.EXPIRING -> AccentContainer to AccentInk
        StockStatus.NORMAL -> PrimaryContainer to Primary
    }
    Pill(text = medicine.expiryShortLabel, container = container, content = content)
}

/**
 * 统计小块：数值 + 说明，可选中高亮，用于首页与家庭页。
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else SurfaceWhite)
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.45f) else Divider,
                RoundedCornerShape(16.dp)
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 13.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    lineHeight = 26.sp
                ),
                color = accent
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    onClear: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, Outline)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = InkMuted,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(
                        MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty() && onClear != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Divider)
                        .clickable { onClear() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "清空",
                        tint = InkSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * 主操作按钮，统一圆角与高度。
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    container: Color = Primary
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(if (compact) 50.dp else 14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(if (compact) 50.dp else 14.dp),
        color = if (enabled) container else container.copy(alpha = 0.4f)
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 18.dp else 14.dp,
                    vertical = if (compact) 9.dp else 13.dp
                ),
            style = MaterialTheme.typography.labelLarge,
            color = OnPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(PrimaryContainer.copy(alpha = 0.55f))
            )
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(SurfaceWhite),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(5.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun SuccessPill(text: String) = Pill(text, SuccessContainer, Success)

@Composable
fun WarningPill(text: String, container: Color = AccentContainer, content: Color = AccentInk) =
    Pill(text, container, content)
