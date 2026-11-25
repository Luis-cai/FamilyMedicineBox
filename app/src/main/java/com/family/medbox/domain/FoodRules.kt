package com.family.medbox.domain

import com.family.medbox.data.model.Medicine

/**
 * 一条「药 × 食物」注意事项。关键词只匹配药名与成分，
 * 命中后给出提示，具体仍以说明书和医生意见为准。
 */
data class FoodRule(
    val title: String,
    val detail: String,
    val keywords: List<String>,
    val level: Level = Level.CAUTION
) {
    enum class Level(val label: String) {
        AVOID("需避免"),
        SPACING("要隔开"),
        CAUTION("需注意")
    }

    fun matches(medicine: Medicine): Boolean {
        val text = (medicine.name + " " + medicine.ingredient).lowercase()
        return keywords.any { text.contains(it.lowercase()) }
    }
}

object FoodRules {

    val all: List<FoodRule> = listOf(
        FoodRule(
            title = "头孢类 + 酒",
            detail = "可能引起面部潮红、心慌、呼吸急促。服药期间和停药后 7 天内都不要喝酒，也别用含酒精的藿香正气水。",
            keywords = listOf("头孢", "cef", "拉氧头孢"),
            level = FoodRule.Level.AVOID
        ),
        FoodRule(
            title = "甲硝唑 / 替硝唑 + 酒",
            detail = "同样可能出现双硫仑样反应，用药期间及停药后 3 天内避免饮酒。",
            keywords = listOf("甲硝唑", "替硝唑", "奥硝唑"),
            level = FoodRule.Level.AVOID
        ),
        FoodRule(
            title = "他汀类 + 西柚",
            detail = "西柚（葡萄柚）会让他汀血药浓度升高，增加肌肉酸痛、肝损伤风险。柚子、柚子汁也要避开。",
            keywords = listOf("他汀", "辛伐他汀", "阿托伐他汀", "洛伐他汀"),
            level = FoodRule.Level.AVOID
        ),
        FoodRule(
            title = "镇静安眠药 + 酒",
            detail = "酒精会放大镇静作用，可能导致呼吸变慢、跌倒。服药当晚不要饮酒。",
            keywords = listOf("地西泮", "艾司唑仑", "阿普唑仑", "唑吡坦", "右佐匹克隆"),
            level = FoodRule.Level.AVOID
        ),
        FoodRule(
            title = "布洛芬 / 阿司匹林 + 酒",
            detail = "两者都刺激胃黏膜，叠加酒精会明显增加胃出血风险。",
            keywords = listOf("布洛芬", "阿司匹林", "双氯芬酸", "洛索洛芬"),
            level = FoodRule.Level.AVOID
        ),
        FoodRule(
            title = "二甲双胍 + 酒",
            detail = "大量饮酒可能诱发乳酸酸中毒，也会让低血糖更难察觉。",
            keywords = listOf("二甲双胍"),
            level = FoodRule.Level.AVOID
        ),
        FoodRule(
            title = "阿仑膦酸钠 / 利塞膦酸钠 + 早餐",
            detail = "必须空腹用一大杯白水送服，至少 30 分钟后再吃早餐或补钙，服后别马上躺下。",
            keywords = listOf("阿仑膦酸钠", "利塞膦酸钠", "唑来膦酸"),
            level = FoodRule.Level.SPACING
        ),
        FoodRule(
            title = "左甲状腺素 + 豆浆 / 牛奶 / 钙铁",
            detail = "高蛋白、钙和铁都会影响吸收，建议间隔 4 小时以上。",
            keywords = listOf("左甲状腺素", "优甲乐", "雷替斯"),
            level = FoodRule.Level.SPACING
        ),
        FoodRule(
            title = "左氧氟沙星 / 四环素类 + 牛奶 / 钙片",
            detail = "牛奶、钙、铁、镁会与药物结合，降低吸收。服药前后 2 小时避开。",
            keywords = listOf("左氧氟沙星", "莫西沙星", "四环素", "多西环素", "米诺环素"),
            level = FoodRule.Level.SPACING
        ),
        FoodRule(
            title = "铁剂 + 茶 / 咖啡 / 牛奶",
            detail = "茶多酚、咖啡因和钙都会妨碍铁吸收。用温水送服，和这些饮品隔开 2 小时。",
            keywords = listOf("硫酸亚铁", "琥珀酸亚铁", "富马酸亚铁", "多糖铁"),
            level = FoodRule.Level.SPACING
        ),
        FoodRule(
            title = "蒙脱石散 + 其他口服药",
            detail = "蒙脱石会吸附其他药物，建议与其他口服药间隔 2 小时以上。",
            keywords = listOf("蒙脱石"),
            level = FoodRule.Level.SPACING
        ),
        FoodRule(
            title = "华法林 + 绿叶菜 / 维生素 K",
            detail = "不是完全不能吃，而是每天摄入量要稳定，避免突然大量吃菠菜、西兰花等深绿叶菜。",
            keywords = listOf("华法林"),
            level = FoodRule.Level.CAUTION
        ),
        FoodRule(
            title = "地平类降压药 + 西柚",
            detail = "西柚可能让硝苯地平、氨氯地平等药物浓度升高，出现头晕、心慌。尽量少吃柚子类水果。",
            keywords = listOf("硝苯地平", "氨氯地平", "非洛地平", "尼群地平"),
            level = FoodRule.Level.CAUTION
        )
    )

    /** 按当前药箱里的药筛出相关条目，并带回涉及的药。 */
    fun matched(medicines: List<Medicine>): List<Pair<FoodRule, List<Medicine>>> =
        all.mapNotNull { rule ->
            val hits = medicines.filter { rule.matches(it) }
            if (hits.isEmpty()) null else rule to hits
        }
}
