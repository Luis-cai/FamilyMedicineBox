package com.family.medbox.domain

import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.StockStatus

enum class CheckVerdict {
    /** 家中已有充足同成分药品，无需购买 */
    ENOUGH,

    /** 家中已有，但余量偏少，可按需补充 */
    LOW,

    /** 只有过期的同成分药品，需先清理再买 */
    ONLY_EXPIRED,

    /** 家中没有同成分药品，可以放心购买 */
    NOT_FOUND,

    /** 用户要买的是不同商品名，但与现有药品同成分，存在叠加风险 */
    SAME_INGREDIENT_RISK
}

data class CheckResult(
    val query: String,
    val ingredient: String?,
    val verdict: CheckVerdict,
    val headline: String,
    val detail: String,
    val matched: List<Medicine>,
    val safetyNote: String?
)

/**
 * 购药决策守卫：把「准备买什么」和「家里已经记着什么」做成分级比对，
 * 在用户掏钱之前给出结论。
 */
class ShoppingGuard(private val knowledge: DrugKnowledge) {

    fun check(rawQuery: String, medicines: List<Medicine>): CheckResult {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            return CheckResult(
                query = query,
                ingredient = null,
                verdict = CheckVerdict.NOT_FOUND,
                headline = "先告诉我要买什么药",
                detail = "输入药名或商品名，我会先帮你查家里还有没有同成分的存货。",
                matched = emptyList(),
                safetyNote = null
            )
        }

        val entry = knowledge.bestMatch(query)
        val queryIngredient = entry?.ingredient
            ?: medicines.firstOrNull { query.contains(it.name) || it.name.contains(query) }?.ingredient

        val matched = if (queryIngredient.isNullOrBlank()) {
            medicines.filter { query.contains(it.name) || it.name.contains(query) }
        } else {
            medicines.filter { it.ingredient == queryIngredient }
        }

        val usableMatched = matched.filter { it.status != StockStatus.EXPIRED }
        val expiredMatched = matched.filter { it.status == StockStatus.EXPIRED }
        val totalQuantity = usableMatched.sumOf { it.quantity }

        val note = queryIngredient?.let { knowledge.entryOf(it)?.note }
        val sameNameInStock = matched.any { it.name.contains(query) }

        val verdict = when {
            usableMatched.isEmpty() && expiredMatched.isNotEmpty() -> CheckVerdict.ONLY_EXPIRED
            usableMatched.isEmpty() -> CheckVerdict.NOT_FOUND
            !sameNameInStock && totalQuantity > 0 -> CheckVerdict.SAME_INGREDIENT_RISK
            totalQuantity <= 2 -> CheckVerdict.LOW
            else -> CheckVerdict.ENOUGH
        }

        val ingredientText = queryIngredient ?: "该药品"
        val (headline, detail) = when (verdict) {
            CheckVerdict.ENOUGH -> "无需重复购买" to
                "家里已有 ${usableMatched.size} 种含「$ingredientText」的药品，合计 $totalQuantity ${usableMatched.first().unit}，余量充足。"
            CheckVerdict.LOW -> "可以先看看家里的余量" to
                "家里还有含「$ingredientText」的药品，但剩余不多（共 $totalQuantity ${usableMatched.first().unit}），确需时再补一盒即可。"
            CheckVerdict.SAME_INGREDIENT_RISK -> "注意：成分重复" to
                "你要买的是另一个品牌，但家里的「${usableMatched.first().name}」成分同为「$ingredientText」，再买容易造成重复用药。"
            CheckVerdict.ONLY_EXPIRED -> "家里的已经过期" to
                "家里有含「$ingredientText」的药品，但 ${expiredMatched.size} 种已过期，请先清理再按需购买。"
            CheckVerdict.NOT_FOUND -> "家里没有同成分药品" to
                "药箱里没有记录含「$ingredientText」的药品，可以放心购买；买回来后记得拍照入库。"
        }

        return CheckResult(
            query = query,
            ingredient = queryIngredient,
            verdict = verdict,
            headline = headline,
            detail = detail,
            matched = matched,
            safetyNote = note
        )
    }
}
