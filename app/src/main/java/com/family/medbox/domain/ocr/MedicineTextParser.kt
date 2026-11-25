package com.family.medbox.domain.ocr

import com.family.medbox.domain.DrugKnowledge
import com.family.medbox.util.DateUtils
import java.time.LocalDate

/**
 * 把药盒上的 OCR 文本整理成结构化字段：名称、成分、规格、剂型、数量、有效期。
 * 纯规则解析，端侧运行，不依赖任何在线服务。
 */
class MedicineTextParser(private val knowledge: DrugKnowledge) {

    data class Parsed(
        val name: String = "",
        val ingredient: String = "",
        val spec: String = "",
        val form: String = "",
        val quantity: Int = 0,
        val unit: String = "份",
        val expiryDate: String? = null
    ) {
        val isUseful: Boolean get() = name.isNotBlank() || ingredient.isNotBlank()
    }

    private val formKeywords = listOf(
        "胶囊", "片剂", "缓释片", "分散片", "咀嚼片", "泡腾片", "颗粒", "口服液",
        "糖浆", "混悬液", "滴剂", "滴眼液", "软膏", "乳膏", "凝胶", "喷雾剂", "气雾剂", "散剂", "丸"
    )

    private val unitKeywords = listOf("片", "粒", "袋", "支", "瓶", "丸", "包", "贴", "ml")

    fun parse(raw: String): Parsed {
        if (raw.isBlank()) return Parsed()
        val text = raw.replace('　', ' ')
        val lines = text.split("\n", " ")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val entry = knowledge.bestMatch(text)
        val ingredient = entry?.ingredient.orEmpty()
        val name = guessName(lines, entry?.aliases.orEmpty())
        val form = formKeywords.firstOrNull { text.contains(it) }.orEmpty()
        val spec = guessSpec(text)
        val (quantity, unit) = guessQuantity(spec, text)
        val expiry = guessExpiry(text)

        return Parsed(
            name = name,
            ingredient = ingredient,
            spec = spec,
            form = form,
            quantity = quantity,
            unit = unit,
            expiryDate = expiry
        )
    }

    private fun guessName(lines: List<String>, aliases: List<String>): String {
        // 优先命中知识库商品名
        val aliasHit = lines.firstOrNull { line ->
            aliases.any { it.length >= 2 && line.contains(it) }
        }
        if (aliasHit != null) return cleanup(aliasHit)

        // 其次找带剂型的行
        val formHit = lines.firstOrNull { line ->
            formKeywords.any { line.contains(it) } && line.length in 3..30
        }
        if (formHit != null) return cleanup(formHit)

        // 兜底：第一行较短的文本
        return lines.firstOrNull { it.length in 3..24 && it.count { c -> c.isDigit() } < 4 }
            ?.let { cleanup(it) }
            .orEmpty()
    }

    private fun cleanup(value: String): String = value
        .replace("国药准字", "")
        .replace("OTC", "")
        .replace("[", "")
        .replace("]", "")
        .trim()
        .take(30)

    private fun guessSpec(text: String): String {
        val regex = Regex("(\\d+(?:\\.\\d+)?\\s*(?:mg|g|ml|毫克|克|毫升|μg|ug|IU)(?:\\s*[×xX*]\\s*\\d+\\s*[片粒袋支瓶丸包])?)")
        return regex.find(text)?.value?.replace(" ", "")?.take(24).orEmpty()
    }

    private fun guessQuantity(spec: String, text: String): Pair<Int, String> {
        val packed = Regex("[×xX*]\\s*(\\d+)\\s*([片粒袋支瓶丸包])").find(spec)
        if (packed != null) {
            val value = packed.groupValues[1].toIntOrNull() ?: 0
            return value to packed.groupValues[2]
        }
        val loose = Regex("(\\d+)\\s*([片粒袋支瓶丸包])").find(text)
        if (loose != null) {
            val value = loose.groupValues[1].toIntOrNull() ?: 0
            return value to loose.groupValues[2]
        }
        return 0 to "份"
    }

    /** 识别「有效期至 / 生产日期 + 有效期时长」两类写法 */
    private fun guessExpiry(text: String): String? {
        val directPatterns = listOf(
            Regex("有效期至[:：]?\\s*(\\d{4})[.\\-/年](\\d{1,2})[.\\-/月]?(\\d{1,2})?"),
            Regex("有效期限[:：]?\\s*(\\d{4})[.\\-/年](\\d{1,2})[.\\-/月]?(\\d{1,2})?"),
            Regex("(\\d{4})[.\\-/年](\\d{1,2})[.\\-/月](\\d{1,2})日?\\s*(?:到期|失效)"),
            Regex("失效日期[:：]?\\s*(\\d{4})[.\\-/年](\\d{1,2})[.\\-/月]?(\\d{1,2})?")
        )
        directPatterns.forEach { regex ->
            regex.find(text)?.let { match ->
                val year = match.groupValues[1].toIntOrNull() ?: return@let
                val month = match.groupValues[2].toIntOrNull() ?: return@let
                val day = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 99
                buildDate(year, month, day)?.let { return DateUtils.toIso(it) }
            }
        }

        // 生产日期 + 有效期 24 个月
        val produced = Regex("生产日期[:：]?\\s*(\\d{4})[.\\-/年](\\d{1,2})[.\\-/月]?(\\d{1,2})?")
            .find(text)
        val validMonths = Regex("有效期[:：]?\\s*(\\d{1,3})\\s*个月").find(text)
        if (produced != null && validMonths != null) {
            val year = produced.groupValues[1].toIntOrNull() ?: return null
            val month = produced.groupValues[2].toIntOrNull() ?: return null
            val day = produced.groupValues.getOrNull(3)?.toIntOrNull() ?: 1
            val months = validMonths.groupValues[1].toIntOrNull() ?: return null
            val base = buildDate(year, month, day) ?: return null
            return DateUtils.toIso(base.plusMonths(months.toLong()).minusDays(1))
        }
        return null
    }

    private fun buildDate(year: Int, month: Int, day: Int): LocalDate? = runCatching {
        if (month !in 1..12) return null
        val safeDay = if (day in 1..31) day else {
            val lastDay = LocalDate.of(year, month, 1).lengthOfMonth()
            lastDay
        }
        LocalDate.of(year, month, safeDay.coerceAtMost(LocalDate.of(year, month, 1).lengthOfMonth()))
    }.getOrNull()
}
