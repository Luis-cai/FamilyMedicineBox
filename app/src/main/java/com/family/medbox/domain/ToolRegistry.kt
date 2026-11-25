package com.family.medbox.domain

import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.model.StockStatus

/**
 * 智能体可调用的领域工具：只做确定性计算，不生成任何医疗建议。
 * 后续接入模型编排时，这里的每个函数都对应一个 function calling 工具。
 */
object ToolRegistry {

    /** 计划对应的库存药品：先精确匹配名称，再按前缀兜底。 */
    fun matchMedicine(plan: MedicationPlan, medicines: List<Medicine>): Medicine? {
        val exact = medicines.firstOrNull { it.name == plan.medicineName }
        if (exact != null) return exact
        val keyword = plan.medicineName.substringBefore(" ").trim()
        if (keyword.isBlank()) return null
        return medicines.firstOrNull { it.name.contains(keyword, true) }
    }

    /** 每天服药次数，按计划里的时间点数量计算。 */
    fun dosesPerDay(plan: MedicationPlan): Int = plan.times.distinct().size

    /** 按当前计划，库存还能撑几天；不足一天记为 0。 */
    fun stockDaysLeft(medicine: Medicine, dosesPerDay: Int): Int? {
        if (dosesPerDay <= 0) return null
        return (medicine.quantity / dosesPerDay).coerceAtLeast(0)
    }

    /** 与计划相关且 30 天内到期的药品，用来提示「先把这盒吃完」。 */
    fun expiringInUse(
        plans: List<MedicationPlan>,
        medicines: List<Medicine>,
        isActive: (MedicationPlan) -> Boolean
    ): List<Pair<Medicine, MedicationPlan>> =
        plans.filter(isActive).mapNotNull { plan ->
            matchMedicine(plan, medicines)
                ?.takeIf { it.status == StockStatus.EXPIRING }
                ?.let { it to plan }
        }
}
