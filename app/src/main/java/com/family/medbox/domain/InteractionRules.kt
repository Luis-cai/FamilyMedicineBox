package com.family.medbox.domain

import com.family.medbox.data.model.Medicine

enum class InteractionSeverity { HIGH, MEDIUM }

data class InteractionWarning(
    val severity: InteractionSeverity,
    val title: String,
    val detail: String,
    val medicineNames: List<String>
)

/**
 * 本地常见用药相互作用规则：按药名/成分关键词配对，不联网也能提示。
 * 仅覆盖常见家庭常备药，提示用于提醒核对，不替代医生和药师的判断。
 */
object InteractionRules {

    private data class Rule(
        val first: List<String>,
        val second: List<String>,
        val severity: InteractionSeverity,
        val title: String,
        val detail: String
    )

    private val rules = listOf(
        Rule(
            first = listOf("阿司匹林", "乙酰水杨酸"),
            second = listOf("布洛芬", "双氯芬酸", "萘普生", "洛索洛芬"),
            severity = InteractionSeverity.HIGH,
            title = "出血风险叠加",
            detail = "两者都属非甾体抗炎药，同服会增加胃出血风险。需要止痛时先咨询医生，避免长期同服。"
        ),
        Rule(
            first = listOf("对乙酰氨基酚", "扑热息痛"),
            second = listOf("感冒灵", "感康", "泰诺", "白加黑", "新康泰克", "复方氨酚烷胺", "氨咖黄敏"),
            severity = InteractionSeverity.HIGH,
            title = "退烧成分可能重复",
            detail = "复方感冒药常含对乙酰氨基酚，叠加服用容易超过安全剂量、损伤肝脏。"
        ),
        Rule(
            first = listOf("阿司匹林", "氯吡格雷"),
            second = listOf("华法林", "利伐沙班", "达比加群", "阿哌沙班"),
            severity = InteractionSeverity.HIGH,
            title = "抗凝与抗血小板同服",
            detail = "出血风险明显升高，必须遵医嘱并定期监测凝血功能。"
        ),
        Rule(
            first = listOf("布洛芬", "双氯芬酸", "萘普生"),
            second = listOf("氨氯地平", "硝苯地平", "缬沙坦", "厄贝沙坦", "依那普利", "美托洛尔"),
            severity = InteractionSeverity.MEDIUM,
            title = "可能减弱降压效果",
            detail = "非甾体抗炎药可能升高血压并影响肾功能，长期同服需监测血压。"
        ),
        Rule(
            first = listOf("蒙脱石"),
            second = emptyList(),
            severity = InteractionSeverity.MEDIUM,
            title = "与其他口服药间隔 2 小时",
            detail = "蒙脱石会吸附其他药物、降低吸收。先服其他药，至少 2 小时后再服蒙脱石。"
        ),
        Rule(
            first = listOf("阿莫西林", "头孢", "阿奇霉素", "左氧氟沙星", "克拉霉素"),
            second = listOf("益生菌", "双歧杆菌", "乳酸菌", "妈咪爱"),
            severity = InteractionSeverity.MEDIUM,
            title = "抗菌药会杀灭益生菌",
            detail = "两者间隔 2 小时以上，抗菌药疗程结束后再补益生菌更合适。"
        ),
        Rule(
            first = listOf("左氧氟沙星", "莫西沙星", "环丙沙星"),
            second = listOf("钙片", "碳酸钙", "铁剂", "硫酸亚铁", "铝碳酸镁", "氢氧化铝"),
            severity = InteractionSeverity.MEDIUM,
            title = "影响抗菌药吸收",
            detail = "钙、铁、铝会与喹诺酮类结合，建议间隔 2 小时以上服用。"
        ),
        Rule(
            first = listOf("头孢"),
            second = listOf("藿香正气水"),
            severity = InteractionSeverity.HIGH,
            title = "警惕双硫仑样反应",
            detail = "藿香正气水含酒精，与头孢同用可能引起面红、心慌、呼吸困难，应避免。"
        ),
        Rule(
            first = listOf("奥美拉唑", "泮托拉唑", "埃索美拉唑"),
            second = listOf("氯吡格雷"),
            severity = InteractionSeverity.MEDIUM,
            title = "可能降低抗血小板效果",
            detail = "部分抑酸药会影响氯吡格雷活化，长期同服需医生调整方案。"
        ),
        Rule(
            first = listOf("阿托伐他汀", "辛伐他汀", "洛伐他汀"),
            second = listOf("红霉素", "克拉霉素"),
            severity = InteractionSeverity.HIGH,
            title = "他汀浓度升高、肌病风险",
            detail = "大环内酯类抗菌药会抑制他汀代谢，出现肌肉酸痛、乏力要及时就医。"
        ),
        Rule(
            first = listOf("氯雷他定", "西替利嗪", "扑尔敏", "苯海拉明"),
            second = listOf("地西泮", "艾司唑仑", "唑吡坦", "安眠"),
            severity = InteractionSeverity.MEDIUM,
            title = "嗜睡作用叠加",
            detail = "抗过敏药与镇静催眠药同服可能加重困倦，服药后避免开车和高空作业。"
        ),
        Rule(
            first = listOf("右美沙芬"),
            second = listOf("氟西汀", "帕罗西汀", "舍曲林", "文拉法辛"),
            severity = InteractionSeverity.HIGH,
            title = "5-羟色胺综合征风险",
            detail = "止咳药右美沙芬与 SSRI 类抗抑郁药同服需医生评估，出现发热、震颤应停药就医。"
        ),
        Rule(
            first = listOf("钙片", "碳酸钙", "葡萄糖酸钙"),
            second = listOf("铁剂", "硫酸亚铁", "琥珀酸亚铁"),
            severity = InteractionSeverity.MEDIUM,
            title = "钙铁吸收互相影响",
            detail = "钙和铁同服会竞争吸收，建议错开 2 小时服用。"
        ),
        Rule(
            first = listOf("阿仑膦酸钠", "双膦酸盐"),
            second = listOf("钙片", "碳酸钙", "牛奶"),
            severity = InteractionSeverity.MEDIUM,
            title = "影响双膦酸盐吸收",
            detail = "阿仑膦酸钠需空腹单服，至少 30 分钟后再进食或补钙。"
        )
    )

    fun check(medicines: List<Medicine>): List<InteractionWarning> {
        if (medicines.size < 2) return emptyList()

        val warnings = mutableListOf<InteractionWarning>()
        val seen = mutableSetOf<String>()
        for (i in medicines.indices) {
            for (j in i + 1 until medicines.size) {
                val a = medicines[i]
                val b = medicines[j]
                rules.forEach { rule ->
                    if (!matches(rule, a, b)) return@forEach
                    val key = "${rule.title}|${minOf(a.id, b.id)}|${maxOf(a.id, b.id)}"
                    if (seen.add(key)) {
                        warnings += InteractionWarning(
                            severity = rule.severity,
                            title = rule.title,
                            detail = rule.detail,
                            medicineNames = listOf(a.name, b.name)
                        )
                    }
                }
            }
        }
        return warnings.sortedBy { if (it.severity == InteractionSeverity.HIGH) 0 else 1 }
    }

    private fun matches(rule: Rule, a: Medicine, b: Medicine): Boolean {
        val textA = searchable(a)
        val textB = searchable(b)
        val aFirst = rule.first.any { textA.contains(it) }
        val bFirst = rule.first.any { textB.contains(it) }
        if (rule.second.isEmpty()) {
            // 「蒙脱石 + 任意口服药」这类规则，排除喷鼻、外用等非口服剂型。
            return (aFirst && isOral(b)) || (bFirst && isOral(a))
        }
        val aSecond = rule.second.any { textA.contains(it) }
        val bSecond = rule.second.any { textB.contains(it) }
        return (aFirst && bSecond) || (bFirst && aSecond)
    }

    private fun isOral(medicine: Medicine): Boolean {
        val form = medicine.form
        return NON_ORAL_MARKERS.none { form.contains(it) }
    }

    private val NON_ORAL_MARKERS = listOf(
        "喷雾", "喷剂", "滴鼻", "滴眼", "滴耳", "外用", "软膏", "乳膏", "贴剂", "栓", "洗剂"
    )

    private fun searchable(medicine: Medicine): String =
        listOf(medicine.name, medicine.ingredient).joinToString(" ")
}
