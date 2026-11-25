package com.family.medbox.domain

import android.content.Context
import org.json.JSONObject

/**
 * 端侧药品知识库：把「商品名 / 别名」映射到「通用名（成分）」。
 * 这是"同成分查重"能力的基础，全部随 App 打包在本地，无需联网。
 */
class DrugKnowledge private constructor(private val entries: List<Entry>) {

    constructor(context: Context) : this(load(context.applicationContext))

    data class Entry(
        val ingredient: String,
        val aliases: List<String>,
        val category: String,
        val note: String,
        val classGroup: String
    )

    fun all(): List<Entry> = entries

    fun entryOf(ingredient: String): Entry? =
        entries.firstOrNull { it.ingredient == ingredient }

    fun classGroupOf(ingredient: String): String? = entryOf(ingredient)?.classGroup

    /** 从任意文本（OCR 结果 / 用户输入）中识别出最强匹配的成分条目 */
    fun bestMatch(text: String): Entry? {
        val normalized = normalize(text)
        if (normalized.isBlank()) return null
        var best: Entry? = null
        var bestLength = 0
        entries.forEach { entry ->
            entry.aliases.forEach { alias ->
                val key = normalize(alias)
                if (key.isNotEmpty() && normalized.contains(key) && key.length > bestLength) {
                    bestLength = key.length
                    best = entry
                }
            }
        }
        return best
    }

    fun detectIngredient(text: String): String? = bestMatch(text)?.ingredient

    /** 输入联想：返回匹配的商品名/成分名，用于搜索建议 */
    fun suggest(query: String, limit: Int = 6): List<String> {
        val key = normalize(query)
        if (key.isBlank()) return emptyList()
        val result = LinkedHashSet<String>()
        entries.forEach { entry ->
            if (normalize(entry.ingredient).contains(key)) result += entry.ingredient
            entry.aliases.forEach { alias ->
                if (normalize(alias).contains(key)) result += alias
            }
        }
        return result.take(limit)
    }

    /** 录入页的快速选择建议 */
    fun commonIngredients(): List<String> = entries.map { it.ingredient }

    private fun normalize(text: String): String =
        text.lowercase().replace(" ", "").replace("\n", "")

    companion object {
        /** 纯逻辑场景（如单元测试）直接注入条目，不依赖 Context。 */
        fun ofEntries(entries: List<Entry>): DrugKnowledge = DrugKnowledge(entries)

        private fun load(context: Context): List<Entry> {
        val json = runCatching {
            context.assets.open("drug_kb.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()

        return runCatching {
            val root = JSONObject(json)
            val array = root.getJSONArray("drugs")
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val aliasesArray = item.optJSONArray("aliases")
                    val aliases = buildList {
                        if (aliasesArray != null) {
                            for (j in 0 until aliasesArray.length()) add(aliasesArray.getString(j))
                        }
                    }
                    add(
                        Entry(
                            ingredient = item.getString("ingredient"),
                            aliases = aliases.ifEmpty { listOf(item.getString("ingredient")) },
                            category = item.optString("category", ""),
                            note = item.optString("note", ""),
                            classGroup = item.optString("classGroup", item.optString("category", ""))
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        }
    }
}
