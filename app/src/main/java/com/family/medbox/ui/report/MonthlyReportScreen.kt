﻿﻿﻿package com.family.medbox.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.medbox.domain.MonthlyReport
import com.family.medbox.domain.MonthlyReportBuilder
import com.family.medbox.ui.components.ExpiryBadge
import com.family.medbox.ui.components.Pill
import com.family.medbox.ui.components.SoftCard
import com.family.medbox.ui.theme.AccentContainer
import com.family.medbox.ui.theme.AccentInk
import com.family.medbox.ui.theme.Background
import com.family.medbox.ui.theme.Danger
import com.family.medbox.ui.theme.Divider
import com.family.medbox.ui.theme.Ink
import com.family.medbox.ui.theme.InkMuted
import com.family.medbox.ui.theme.InkSecondary
import com.family.medbox.ui.theme.OnPrimary
import com.family.medbox.ui.theme.Primary
import com.family.medbox.ui.theme.PrimaryContainer
import com.family.medbox.ui.theme.PrimaryDark
import com.family.medbox.ui.theme.SurfaceWhite

/**
 * 月度药箱体检卡：一屏可截图分享的本地报告。
 */
@Composable
fun MonthlyReportScreen(
    report: MonthlyReport,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        Surface(color = SurfaceWhite) {
            Column {
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
                            text = "月度药箱体检卡",
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink
                        )
                        Text(
                            text = "本地统计 · 截图即可分享给家人",
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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            ReportHero(report)
            Spacer(Modifier.height(14.dp))
            ReportStats(report)
            Spacer(Modifier.height(20.dp))

            ReportLabel("体检解读", "按优先级排好的本月动作")
            Spacer(Modifier.height(8.dp))
            SoftCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                report.actions.forEachIndexed { index, action ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(PrimaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = action,
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSecondary,
                            lineHeight = 21.sp
                        )
                    }
                    if (index != report.actions.lastIndex) Spacer(Modifier.height(12.dp))
                }
            }

            if (report.attention.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                ReportLabel("需要关注的药", "过期与 30 天内到期")
                Spacer(Modifier.height(8.dp))
                report.attention.take(8).forEach { medicine ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceWhite,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Divider)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = medicine.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Ink,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = listOf(medicine.ingredient, medicine.location)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                        .ifBlank { "未记录存放位置" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted
                                )
                            }
                            ExpiryBadge(medicine)
                        }
                    }
                }
            }

            if (report.duplicates.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                ReportLabel("重复成分", "同一种成分不要在药箱里越堆越多")
                Spacer(Modifier.height(8.dp))
                report.duplicates.forEach { group ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = AccentContainer.copy(alpha = 0.6f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = group.ingredient,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Ink,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.size(6.dp))
                                Pill(
                                    text = "${group.medicines.size} 盒",
                                    container = SurfaceWhite.copy(alpha = 0.75f),
                                    content = AccentInk
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = group.medicines.joinToString("、") { it.name },
                                style = MaterialTheme.typography.labelSmall,
                                color = InkSecondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PrimaryContainer.copy(alpha = 0.55f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.size(9.dp))
                Text(
                    text = "报告由本机药箱数据生成，不含任何账号信息，也不会联网上传。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    lineHeight = 17.sp
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReportHero(report: MonthlyReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Primary, PrimaryDark)))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.monthLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = OnPrimary.copy(alpha = 0.75f),
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = report.headline,
                    style = MaterialTheme.typography.titleLarge,
                    color = OnPrimary,
                    lineHeight = 26.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = MonthlyReportBuilder.subtitle(report),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnPrimary.copy(alpha = 0.82f)
                )
            }
            Spacer(Modifier.size(10.dp))
            ScoreRing(score = report.score)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(OnPrimary.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = OnPrimary.copy(alpha = 0.9f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.size(5.dp))
            Text(
                text = MonthlyReportBuilder.generatedAt(),
                style = MaterialTheme.typography.labelSmall,
                color = OnPrimary.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun ScoreRing(score: Int) {
    Box(
        modifier = Modifier.size(76.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = androidx.compose.ui.geometry.Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = OnPrimary.copy(alpha = 0.22f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = OnPrimary,
                startAngle = -90f,
                sweepAngle = 360f * score / 100f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
                color = OnPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "健康分",
                style = MaterialTheme.typography.labelSmall,
                color = OnPrimary.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun ReportStats(report: MonthlyReport) {
    val tiles = listOf(
        Triple("在库药品", report.total.toString(), Primary),
        Triple("30 天内到期", report.expiring.size.toString(), AccentInk),
        Triple("已过期", report.expired.size.toString(), Danger),
        Triple("重复成分", report.duplicates.size.toString(), PrimaryDark)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value, accent) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceWhite)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                            color = accent
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportLabel(title: String, hint: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(2.dp))
        Text(text = hint, style = MaterialTheme.typography.labelSmall, color = InkMuted)
    }
}
