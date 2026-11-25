package com.family.medbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.family.medbox.domain.AgentBrain
import com.family.medbox.ui.theme.InkMuted
import com.family.medbox.ui.theme.Primary

/** Agent 推理轨迹：每行一个已调用的工具或推理阶段。 */
@Composable
fun StepTrace(
    steps: List<AgentBrain.Step>,
    modifier: Modifier = Modifier,
    limit: Int = 4
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        steps.take(limit).forEach { step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Primary.copy(alpha = 0.55f))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = step.detail.takeIf { it.isNotBlank() }
                        ?.let { "${step.label} · $it" }
                        ?: step.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                    maxLines = 1
                )
            }
        }
    }
}
