package com.family.medbox.domain

import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.StockStatus
import com.family.medbox.util.DateUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DuplicateGroup(val ingredient: String, val medicines: List<Medicine>)

/**
 * 月度药箱体检卡：把「在库 / 临期 / 过期 / 重复成分 / 余量不足」汇总成一张
 * 可以在家庭群里截图分享的本地报告。统计只在本机完成。
 */
data class MonthlyReport(
    val monthLabel: String,
    val total: Int,
    val expired: List<Medicine>,
    val expiring: List<Medicine>,
    val duplicates: List<DuplicateGroup>,
    val lowStock: List<Medicine>,
    val score: Int,
    val headline: String,
    val actions: List<String>
) {
    val normalCount: Int get() = total - expired.size - expiring.size
    val attention: List<Medicine> get() = expired + expiring
}

object MonthlyReportBuilder {

    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月")

    fun build(medicines: List<Medicine>): MonthlyReport {
        val expired = medicines
            .filter { it.status == StockStatus.EXPIRED }
            .sortedBy { it.daysLeft ?: 0L }
        val expiring = medicines
            .filter { it.status == StockStatus.EXPIRING }
            .sortedBy { it.daysLeft ?: Long.MAX_VALUE }
        val duplicates = medicines
            .filter { it.usable && it.ingredient.isNotBlank() }
            .groupBy { it.ingredient }
            .filter { it.value.size > 1 }
            .map { DuplicateGroup(it.key, it.value) }
            .sortedByDescending { it.medicines.size }
        val lowStock = medicines.filter { it.usable && it.quantity <= 2 }

        val score = (100 - expired.size * 18 - expiring.size * 6 - duplicates.size * 8)
            .coerceIn(30, 100)

        val headline = when {
            expired.isNotEmpty() -> "先清掉过期药，再谈备药"
            duplicates.isNotEmpty() -> "成分有重复，别再买重了"
            expiring.isNotEmpty() -> "整体可控，临期药要先用"
            medicines.isEmpty() -> "药箱还是空的，先建个底"
            else -> "这个月药箱状态很稳"
        }

        val actions = buildList {
            if (expired.isNotEmpty()) {
                add("清理 ${expired.size} 种过期药，避免家人误服")
            }
            if (expiring.isNotEmpty()) {
                add("优先用完 ${expiring.size} 种 30 天内到期的药")
            }
            if (duplicates.isNotEmpty()) {
                add("${duplicates.size} 组同成分药，购买前先查重")
            }
            if (lowStock.isNotEmpty()) {
                add("${lowStock.size} 种药余量不足 3 份，按需补货")
            }
            if (isEmpty()) {
                add("结构完整，按日常用量补充即可")
            }
        }

        return MonthlyReport(
            monthLabel = LocalDate.now().format(monthFormatter),
            total = medicines.size,
            expired = expired,
            expiring = expiring,
            duplicates = duplicates,
            lowStock = lowStock,
            score = score,
            headline = headline,
            actions = actions
        )
    }

    fun empty(): MonthlyReport = build(emptyList())

    /** 本月体检卡的一句话副标题 */
    fun subtitle(report: MonthlyReport): String = when {
        report.total == 0 -> "还没有药品，先录入第一盒"
        report.expired.isNotEmpty() -> "${report.expired.size} 种已过期，需要处理"
        report.expiring.isNotEmpty() -> "${report.expiring.size} 种即将到期"
        else -> "全部药品都在有效期内"
    }

    fun generatedAt(): String {
        val today = DateUtils.today()
        return "${today.monthValue} 月 ${today.dayOfMonth} 日 · 本机生成"
    }
}
