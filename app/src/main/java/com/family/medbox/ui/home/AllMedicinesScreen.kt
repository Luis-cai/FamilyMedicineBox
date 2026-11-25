package com.family.medbox.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.Member
import com.family.medbox.data.model.StockStatus
import com.family.medbox.ui.components.AppSearchField
import com.family.medbox.ui.components.ChoicePill
import com.family.medbox.ui.components.EmptyState
import com.family.medbox.ui.components.ExpiryBadge
import com.family.medbox.ui.components.MemberAvatar
import com.family.medbox.ui.components.SectionHeader
import com.family.medbox.ui.theme.AccentInk
import com.family.medbox.ui.theme.Background
import com.family.medbox.ui.theme.Danger
import com.family.medbox.ui.theme.Divider
import com.family.medbox.ui.theme.Ink
import com.family.medbox.ui.theme.HeroTint
import com.family.medbox.ui.theme.InkMuted
import com.family.medbox.ui.theme.InkSecondary
import com.family.medbox.ui.theme.MemberPalette
import com.family.medbox.ui.theme.OnPrimary
import com.family.medbox.ui.theme.Outline
import com.family.medbox.ui.theme.Primary
import com.family.medbox.ui.theme.Success
import com.family.medbox.ui.theme.SurfaceWhite

/** 药品状态筛选，首页与清单页共用。 */
enum class StockFilter {
    ALL,
    EXPIRING,
    EXPIRED
}

/**
 * 全部药品：从首页拆出来的完整清单。
 * 首页只回答「今天该做什么」，找具体某盒药来这里。
 */
@Composable
fun AllMedicinesScreen(
    medicines: List<Medicine>,
    members: List<Member>,
    initialFilter: StockFilter = StockFilter.ALL,
    onBack: () -> Unit,
    onSelectMedicine: (Medicine) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var memberFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var filter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter) }

    val memberById = remember(members) { members.associateBy { it.id } }
    val expiringCount = medicines.count { it.status == StockStatus.EXPIRING }
    val expiredCount = medicines.count { it.status == StockStatus.EXPIRED }

    val filtered = remember(medicines, query, memberFilter, filter) {
        medicines
            .filter { medicine ->
                val matchMember = memberFilter == null || medicine.memberId == memberFilter
                val matchStatus = when (filter) {
                    StockFilter.ALL -> true
                    StockFilter.EXPIRING -> medicine.status == StockStatus.EXPIRING
                    StockFilter.EXPIRED -> medicine.status == StockStatus.EXPIRED
                }
                val matchQuery = query.isBlank() ||
                    medicine.name.contains(query, true) ||
                    medicine.ingredient.contains(query, true) ||
                    medicine.category.contains(query, true) ||
                    medicine.location.contains(query, true)
                matchMember && matchStatus && matchQuery
            }
            .sortedBy { it.daysLeft ?: Long.MAX_VALUE }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to HeroTint,
                    0.18f to Background,
                    1f to Background
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = SurfaceWhite) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Ink)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "全部药品",
                                style = MaterialTheme.typography.titleLarge,
                                color = Ink
                            )
                            Text(
                                text = "按有效期、成员筛选，随时找到药",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted
                            )
                        }
                    }
                    HorizontalDivider(color = Divider)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    AppSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "搜索药品、成分，如「布洛芬」",
                        leadingIcon = Icons.Rounded.Search,
                        onClear = { query = "" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        item {
                            ChoicePill(
                                label = "全部 ${medicines.size}",
                                selected = filter == StockFilter.ALL,
                                onClick = { filter = StockFilter.ALL }
                            )
                        }
                        item {
                            ChoicePill(
                                label = "30 天内到期 $expiringCount",
                                selected = filter == StockFilter.EXPIRING,
                                onClick = { filter = StockFilter.EXPIRING }
                            )
                        }
                        item {
                            ChoicePill(
                                label = "已过期 $expiredCount",
                                selected = filter == StockFilter.EXPIRED,
                                onClick = { filter = StockFilter.EXPIRED }
                            )
                        }
                    }
                }
                if (members.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            item {
                                MemberFilterChip(
                                    label = "全部成员",
                                    selected = memberFilter == null
                                ) { memberFilter = null }
                            }
                            items(members) { member ->
                                val accent =
                                    MemberPalette[member.colorIndex.mod(MemberPalette.size)]
                                val selected = memberFilter == member.id
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (selected) accent.copy(alpha = 0.12f) else SurfaceWhite
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) accent.copy(alpha = 0.55f) else Outline,
                                            RoundedCornerShape(50)
                                        )
                                        .clickable {
                                            memberFilter = if (selected) null else member.id
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MemberAvatar(
                                        text = member.name,
                                        colorIndex = member.colorIndex,
                                        size = 18.dp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = member.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) accent else InkSecondary
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    val unfiltered = filter == StockFilter.ALL && memberFilter == null && query.isBlank()
                    SectionHeader(
                        title = if (unfiltered) "在库药品" else "筛选结果",
                        // 未筛选时「全部 21」胶囊已经给了总数，这里不再重复
                        trailing = if (unfiltered) null else "共 ${filtered.size} 种",
                        trailingInline = true,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.Inventory2,
                            title = if (medicines.isEmpty()) "药箱还是空的" else "这里还没有匹配的药品",
                            description = if (medicines.isEmpty()) {
                                "回首页点右下角「＋」拍照、语音或手动录入。"
                            } else {
                                "换个关键词，或调整上方的筛选条件。"
                            }
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { medicine ->
                        MedicineCard(
                            medicine = medicine,
                            member = medicine.memberId?.let { memberById[it] },
                            onClick = { onSelectMedicine(medicine) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Primary else SurfaceWhite)
            .border(1.dp, if (selected) Primary else Outline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) OnPrimary else InkSecondary
        )
    }
}

@Composable
private fun MedicineCard(
    medicine: Medicine,
    member: Member?,
    onClick: () -> Unit
) {
    val accent = when (medicine.status) {
        StockStatus.EXPIRED -> Danger
        StockStatus.EXPIRING -> AccentInk
        StockStatus.NORMAL -> Success
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp),
        shape = RoundedCornerShape(22.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, Divider),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.11f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = medicine.name.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medicine.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (member != null) {
                        MemberAvatar(text = member.name, colorIndex = member.colorIndex, size = 15.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = member.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                    Text(
                        text = medicine.summary.ifBlank { "待补充成分信息" },
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                ExpiryBadge(medicine)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = medicine.stockLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSecondary
                )
            }
        }
    }
}
