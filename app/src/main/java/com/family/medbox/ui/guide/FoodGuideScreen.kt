package com.family.medbox.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Restaurant
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.medbox.data.model.Medicine
import com.family.medbox.domain.FoodRule
import com.family.medbox.domain.FoodRules
import com.family.medbox.ui.components.Pill
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

/**
 * 服药饮食禁忌：用当前药箱里的药去匹配「药 × 食物」规则，
 * 先列命中的，再把全部条目做成可展开的速查表。
 */
@Composable
fun FoodGuideScreen(
    medicines: List<Medicine>,
    onClose: () -> Unit
) {
    var showAll by remember { mutableStateOf(false) }
    val matched = remember(medicines) { FoodRules.matched(medicines) }
    val matchedRules = matched.map { it.first }.toSet()
    val rest = FoodRules.all.filterNot { it in matchedRules }

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
                        text = "服药饮食禁忌",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink
                    )
                    Text(
                        text = "按药箱里的药自动匹配",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 14.dp)
        ) {
            SoftCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                padding = 16.dp,
                background = PrimaryContainer
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(SurfaceWhite),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Restaurant,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${medicines.size} 种在库药品 · ${matched.size} 条相关提醒",
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink
                        )
                        Text(
                            text = "根据药名和成分匹配，具体以说明书和医生意见为准",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (matched.isEmpty()) {
                SoftCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    padding = 18.dp
                ) {
                    Text(
                        text = "当前药箱里没有命中常见的饮食禁忌。补充药品成分后再来看看，匹配会更准。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                        lineHeight = 19.sp
                    )
                }
            } else {
                matched.forEach { (rule, hits) ->
                    RuleCard(
                        rule = rule,
                        involved = hits.joinToString("、") { it.name },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "全部速查",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (showAll) "收起" else "展开 ${FoodRules.all.size} 条",
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showAll = !showAll }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (showAll) {
                rest.forEach { rule ->
                    RuleCard(
                        rule = rule,
                        involved = "",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 3.dp)
                    )
                }
            } else {
                Text(
                    text = "还有 ${rest.size} 条与当前药品无关的常见注意事项，展开可查看。",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "这些提示不能替代说明书。正在服用的处方药，调整饮食前先问医生或药师。",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun RuleCard(
    rule: FoodRule,
    involved: String,
    modifier: Modifier = Modifier
) {
    val (container, content) = when (rule.level) {
        FoodRule.Level.AVOID -> DangerContainer to Danger
        FoodRule.Level.SPACING -> AccentContainer to AccentInk
        FoodRule.Level.CAUTION -> PrimaryContainer to Primary
    }
    SoftCard(modifier = modifier, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(text = rule.level.label, container = container, content = content)
            Spacer(Modifier.width(8.dp))
            Text(
                text = rule.title,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = rule.detail,
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary,
            lineHeight = 19.sp
        )
        if (involved.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Background)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(content)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "涉及：$involved",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
