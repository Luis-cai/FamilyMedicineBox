package com.family.medbox.ui.plan

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.model.Member
import com.family.medbox.ui.components.MemberAvatar
import com.family.medbox.ui.theme.Background
import com.family.medbox.ui.theme.Danger
import com.family.medbox.ui.theme.DangerContainer
import com.family.medbox.ui.theme.Divider
import com.family.medbox.ui.theme.Ink
import com.family.medbox.ui.theme.InkMuted
import com.family.medbox.ui.theme.InkSecondary
import com.family.medbox.ui.theme.OnPrimary
import com.family.medbox.ui.theme.Primary
import com.family.medbox.ui.theme.PrimaryContainer
import com.family.medbox.ui.theme.SurfaceWhite
import com.family.medbox.util.DateUtils
import java.time.LocalTime

private data class ElderDose(
    val plan: MedicationPlan,
    val time: String,
    val checked: Boolean
)

/**
 * 大字版「今天的药」：只保留长辈要吃的药和一个大按钮，
 * 点一下就算打卡，家人那边的今日进度随之更新。
 */
@Composable
fun ElderDoseScreen(
    plans: List<MedicationPlan>,
    members: List<Member>,
    todayChecked: Set<String>,
    onToggleCheck: (MedicationPlan, String) -> Unit,
    onClose: () -> Unit
) {
    val today = DateUtils.today()
    val nowLabel = String.format("%02d:%02d", LocalTime.now().hour, LocalTime.now().minute)
    val careMembers = members.filter { it.needsCare }
    val groups = careMembers.mapNotNull { member ->
        val doses = plans
            .filter { it.memberId == member.id && it.activeOn(today) }
            .flatMap { plan ->
                plan.times.sorted().map { time ->
                    ElderDose(plan = plan, time = time, checked = "${plan.id}@$time" in todayChecked)
                }
            }
            .sortedBy { it.time }
        if (doses.isEmpty()) null else member to doses
    }

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
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Ink)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "今天的药",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 30.sp,
                            lineHeight = 36.sp
                        ),
                        color = Ink,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "吃完点一下大按钮就好",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            if (groups.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, Divider)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "今天没有需要照护的服药安排",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "先在「家庭」页把长辈标记为「重点关注」，" +
                                "再给他们添加服药计划，这里就会出现大字打卡。",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            groups.forEach { (member, doses) ->
                val done = doses.count { it.checked }
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MemberAvatar(text = member.name, colorIndex = member.colorIndex, size = 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        color = Ink,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "今天 $done / ${doses.size} 次",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (done == doses.size) Primary else InkMuted
                    )
                }
                Spacer(Modifier.height(4.dp))

                doses.forEach { dose ->
                    ElderDoseCard(
                        dose = dose,
                        overdue = !dose.checked && dose.time <= nowLabel,
                        onToggle = { onToggleCheck(dose.plan, dose.time) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = "点完按钮，家人就能看到今天的进度；点错了再点一下可以撤销。",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ElderDoseCard(dose: ElderDose, overdue: Boolean, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, if (dose.checked) PrimaryContainer else Divider)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dose.time,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 34.sp,
                        lineHeight = 38.sp
                    ),
                    color = when {
                        dose.checked -> InkMuted
                        overdue -> Danger
                        else -> Ink
                    },
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(10.dp))
                if (overdue) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(DangerContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "已经过点",
                            style = MaterialTheme.typography.labelMedium,
                            color = Danger
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = dose.plan.medicineName,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    lineHeight = 30.sp
                ),
                color = Ink,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (dose.plan.dose.isBlank()) "按计划用量" else dose.plan.dose,
                style = MaterialTheme.typography.bodyLarge,
                color = InkSecondary
            )
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (dose.checked) PrimaryContainer else Primary)
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (dose.checked) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (dose.checked) "已经吃过了" else "我吃了",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        lineHeight = 26.sp
                    ),
                    color = if (dose.checked) Primary else OnPrimary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
