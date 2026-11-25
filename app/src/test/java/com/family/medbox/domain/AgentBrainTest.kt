package com.family.medbox.domain

import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.Member
import com.family.medbox.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentBrainTest {

    private val knowledge = DrugKnowledge.ofEntries(
        listOf(
            DrugKnowledge.Entry(
                ingredient = "布洛芬",
                aliases = listOf("布洛芬", "芬必得"),
                category = "解热镇痛",
                note = "退烧止痛",
                classGroup = "NSAIDs"
            ),
            DrugKnowledge.Entry(
                ingredient = "对乙酰氨基酚",
                aliases = listOf("对乙酰氨基酚", "泰诺", "扑热息痛"),
                category = "解热镇痛",
                note = "退烧止痛",
                classGroup = "解热镇痛"
            )
        )
    )
    private val brain = AgentBrain(knowledge, ShoppingGuard(knowledge), InsightEngine(knowledge))

    private val members = listOf(Member(id = 1L, name = "我", relation = "本人", isSelf = true))

    private fun medicine(
        name: String,
        ingredient: String = name,
        quantity: Int = 10,
        expiryDays: Long = 120
    ) = Medicine(
        id = 1L,
        name = name,
        ingredient = ingredient,
        quantity = quantity,
        expiryDate = DateUtils.toIso(DateUtils.today().plusDays(expiryDays))
    )

    @Test
    fun `空输入直接进入问候`() {
        val reply = brain.answer("", listOf(medicine("布洛芬")), members)
        assertTrue(reply.steps.isNotEmpty())
        assertTrue(reply.suggestions.isNotEmpty())
    }

    @Test
    fun `库存查询会命中药箱里的药`() {
        val reply = brain.answer("家里还有布洛芬吗", listOf(medicine("布洛芬")), members)
        assertTrue(reply.text.contains("布洛芬"))
        assertTrue(reply.steps.isNotEmpty())
    }

    @Test
    fun `过期药会被标出来`() {
        val reply = brain.answer("哪些药过期了", listOf(medicine("布洛芬", expiryDays = -3)), members)
        assertTrue(reply.text.contains("过期"))
        assertTrue(reply.steps.isNotEmpty())
    }

    @Test
    fun `一句话生成服药计划草案`() {
        val reply = brain.answer("把布洛芬设成早晚各一粒，吃 5 天", listOf(medicine("布洛芬")), members)
        val action = reply.actions.firstOrNull { it.kind == AgentBrain.Action.Kind.CREATE_PLAN }
        assertTrue("应给出创建计划动作", action != null)
        val draft = action!!.plan!!
        assertEquals("布洛芬", draft.medicineName)
        assertEquals(5, draft.days)
        assertEquals(2, draft.times.size)
    }

    @Test
    fun `多轮追问可以承接上一轮的药`() {
        val ibuprofen = medicine("布洛芬")
        val first = brain.answer("家里还有布洛芬吗", listOf(ibuprofen), members)
        val reply = brain.answer("那它还剩多少", listOf(ibuprofen), members, memory = first.memory)
        assertTrue(reply.text.contains("布洛芬"))
    }

    @Test
    fun `药箱体检至少执行四项巡检`() {
        val reply = brain.answer("帮我做个药箱体检", listOf(medicine("布洛芬")), members)
        assertTrue("巡检步骤不应少于 4 步", reply.steps.size >= 4)
    }
}
