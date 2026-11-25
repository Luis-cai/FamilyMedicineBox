package com.family.medbox.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.family.medbox.domain.StarterDrug
import com.family.medbox.domain.StarterKit
import com.family.medbox.ui.theme.Divider
import com.family.medbox.ui.theme.Ink
import com.family.medbox.ui.theme.InkMuted
import com.family.medbox.ui.theme.InkSecondary
import com.family.medbox.ui.theme.OnPrimary
import com.family.medbox.ui.theme.Outline
import com.family.medbox.ui.theme.Primary
import com.family.medbox.ui.theme.PrimaryContainer
import com.family.medbox.ui.theme.SurfaceWhite

/**
 * 家庭常备药模板选择器：按场景分组勾选，一次写入多盒药。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarterKitSheet(
    ownedIngredients: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (List<StarterDrug>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = remember { mutableStateListOf<StarterDrug>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceWhite,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Outline) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "家庭常备药模板",
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "按家庭常见场景勾选，一次加入药箱；有效期稍后补录。",
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
                StarterKit.groups.forEach { group ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${group.drugs.size} 种 · ${group.hint}",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    group.drugs.forEach { drug ->
                        val isSelected = selected.contains(drug)
                        val owned = drug.ingredient in ownedIngredients
                        StarterDrugRow(
                            drug = drug,
                            selected = isSelected,
                            owned = owned,
                            onClick = {
                                if (isSelected) selected.remove(drug) else selected.add(drug)
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "已选 ${selected.size} 种",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected.isEmpty()) InkMuted else Primary
                    )
                    Text(
                        text = "加入后有效期为待补录",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }
                PrimaryButton(
                    text = "加入药箱",
                    onClick = { onAdd(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                    compact = true,
                    modifier = Modifier.width(120.dp)
                )
            }
        }
    }
}

@Composable
private fun StarterDrugRow(
    drug: StarterDrug,
    selected: Boolean,
    owned: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) PrimaryContainer else SurfaceWhite,
        border = BorderStroke(1.dp, if (selected) Primary.copy(alpha = 0.5f) else Divider)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (selected) Primary else SurfaceWhite)
                    .border(1.dp, if (selected) Primary else Outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = OnPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = drug.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink,
                        fontWeight = FontWeight.Medium
                    )
                    if (owned) {
                        Spacer(Modifier.width(6.dp))
                        Pill(
                            text = "已有同成分",
                            container = Divider,
                            content = InkMuted
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOf(drug.spec, drug.form, drug.category)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSecondary
                )
            }
        }
    }
}
