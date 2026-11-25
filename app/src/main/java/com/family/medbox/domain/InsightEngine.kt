package com.family.medbox.domain

import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.StockStatus

enum class InsightSeverity { ALERT, WARNING, INFO }

data class Insight(
    val id: String,
    val severity: InsightSeverity,
    val title: String,
    val detail: String,
    val medicineIds: List<Long> = emptyList()
)

/**
 * 主动洞察引擎：不依赖用户提问，直接从药箱记忆里推导出「该被看见」的信息。
 * 过期清理、临期预警、同成分重复，是家庭用药最容易出问题的三件事。
 */
class InsightEngine(private val knowledge: DrugKnowledge) {

    fun build(medicines: List<Medicine>): List<Insight> {
        val insights = mutableListOf<Insight>()

        val expired = medicines.filter { it.status == StockStatus.EXPIRED }
        if (expired.isNotEmpty()) {
            insights += Insight(
                id = "expired",
                severity = InsightSeverity.ALERT,
                title = "${expired.size} 种药品已过期",
                detail = expired.joinToString("、") { it.name } + "。建议尽快清理，避免家人误服。",
                medicineIds = expired.map { it.id }
            )
        }

        val expiring = medicines
            .filter { it.status == StockStatus.EXPIRING }
            .sortedBy { it.daysLeft ?: Long.MAX_VALUE }
        if (expiring.isNotEmpty()) {
            val nearest = expiring.first()
            insights += Insight(
                id = "expiring",
                severity = InsightSeverity.WARNING,
                title = "${expiring.size} 种药品 30 天内到期",
                detail = "最紧急的是「${nearest.name}」，${nearest.expiryShortLabel}。计划用药可以从这些开始。",
                medicineIds = expiring.map { it.id }
            )
        }

        val duplicates = medicines
            .filter { it.usable && it.ingredient.isNotBlank() }
            .groupBy { it.ingredient }
            .filter { it.value.size > 1 }
        if (duplicates.isNotEmpty()) {
            val first = duplicates.entries.first()
            insights += Insight(
                id = "duplicate",
                severity = InsightSeverity.INFO,
                title = "${duplicates.size} 组药品成分重复",
                detail = "「${first.key}」有 ${first.value.size} 个品牌在库存中，购买新药前记得先查一查。",
                medicineIds = duplicates.values.flatten().map { it.id }
            )
        }

        val lowStock = medicines.filter { it.usable && it.quantity <= 2 }
        if (lowStock.isNotEmpty()) {
            insights += Insight(
                id = "low",
                severity = InsightSeverity.INFO,
                title = "${lowStock.size} 种药品余量偏少",
                detail = lowStock.joinToString("、") { "「${it.name}」${it.stockLabel}" } + "，可考虑补充。",
                medicineIds = lowStock.map { it.id }
            )
        }

        if (insights.isEmpty()) {
            insights += Insight(
                id = "healthy",
                severity = InsightSeverity.INFO,
                title = "药箱状态健康",
                detail = "暂时没有过期或临期药品，继续记录每次购药就不会乱。"
            )
        }

        return insights.sortedBy {
            when (it.severity) {
                InsightSeverity.ALERT -> 0
                InsightSeverity.WARNING -> 1
                InsightSeverity.INFO -> 2
            }
        }
    }

    /** 是否有需要主动弹出的通知内容 */
    fun hasUrgent(medicines: List<Medicine>): Boolean =
        medicines.any { it.status != StockStatus.NORMAL }

    fun knowledgeNote(ingredient: String): String? = knowledge.entryOf(ingredient)?.note
}
