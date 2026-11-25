package com.family.medbox.data.model

import com.family.medbox.util.DateUtils

/** 家庭成员 */
data class Member(
    val id: Long = 0L,
    val name: String,
    val relation: String = "家庭成员",
    val birthday: String? = null,
    val colorIndex: Int = 0,
    val isSelf: Boolean = false,
    /** 老人或记性不好的成员：多次补提醒，并单独显示今日打卡进度。 */
    val needsCare: Boolean = false
) {
    val avatarText: String get() = name.take(1)
}

enum class StockStatus { EXPIRED, EXPIRING, NORMAL }

/** 家庭药箱中的一盒/一瓶药 */
data class Medicine(
    val id: Long = 0L,
    val name: String,
    val ingredient: String = "",
    val category: String = "",
    val spec: String = "",
    val form: String = "",
    val quantity: Int = 0,
    val unit: String = "份",
    val expiryDate: String? = null,
    val memberId: Long? = null,
    val location: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val daysLeft: Long? get() = DateUtils.daysLeft(expiryDate)

    val status: StockStatus
        get() {
            val days = daysLeft ?: return StockStatus.NORMAL
            return when {
                days < 0L -> StockStatus.EXPIRED
                days <= 30L -> StockStatus.EXPIRING
                else -> StockStatus.NORMAL
            }
        }

    val statusLabel: String
        get() = when (status) {
            StockStatus.EXPIRED -> "已过期"
            StockStatus.EXPIRING -> "临期"
            StockStatus.NORMAL -> "正常"
        }

    /** 用于列表展示的一行副标题 */
    val summary: String
        get() = listOfNotNull(
            ingredient.takeIf { it.isNotBlank() }?.let { "成分 $it" },
            spec.takeIf { it.isNotBlank() },
            form.takeIf { it.isNotBlank() }
        ).joinToString(" · ")

    val stockLabel: String get() = "剩余 $quantity$unit"

    val expiryShortLabel: String
        get() {
            val days = daysLeft ?: return "未填有效期"
            return when {
                days < 0L -> "已过期 ${-days} 天"
                days == 0L -> "今天到期"
                days <= 30L -> "剩 $days 天"
                else -> DateUtils.monthDay(expiryDate) + " 到期"
            }
        }

    /** 是否可以正常购买/服用 */
    val usable: Boolean get() = status != StockStatus.EXPIRED
}
