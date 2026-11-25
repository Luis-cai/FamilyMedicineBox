package com.family.medbox.domain

import com.family.medbox.data.model.DoseTracker
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseTrackerTest {

    private val today = DateUtils.today()

    private fun plan(
        id: String = "p1",
        times: List<String> = listOf("08:00", "20:00"),
        startDate: String = DateUtils.toIso(today),
        days: Int = 5
    ) = MedicationPlan(
        id = id,
        medicineName = "布洛芬",
        times = times,
        startDate = startDate,
        days = days
    )

    @Test
    fun `疗程日期包含开始日与最后一天`() {
        val item = plan(startDate = DateUtils.toIso(today.minusDays(1)), days = 3)
        assertFalse(item.activeOn(today.minusDays(2)))
        assertTrue(item.activeOn(today.minusDays(1)))
        assertTrue(item.activeOn(today))
        assertTrue(item.activeOn(today.plusDays(1)))
        assertFalse(item.activeOn(today.plusDays(2)))
        assertEquals(today.plusDays(1), item.lastDate())
    }

    @Test
    fun `过期计划不会计入今日进度`() {
        val expired = plan(startDate = DateUtils.toIso(today.minusDays(10)), days = 3)
        val (done, total) = DoseTracker.dailyProgress(listOf(expired), emptyMap(), today)
        assertEquals(0, done)
        assertEquals(0, total)
    }

    @Test
    fun `打卡记录按计划与时间点匹配`() {
        val item = plan()
        val checks = mapOf(
            DateUtils.toIso(today) to setOf(item.checkKey("08:00"))
        )
        val (done, total) = DoseTracker.dailyProgress(listOf(item), checks, today)
        assertEquals(1, done)
        assertEquals(2, total)
    }

    @Test
    fun `其他日期的打卡不会串到今天`() {
        val item = plan()
        val checks = mapOf(
            DateUtils.toIso(today.minusDays(1)) to setOf(item.checkKey("08:00"))
        )
        val (done, total) = DoseTracker.dailyProgress(listOf(item), checks, today)
        assertEquals(0, done)
        assertEquals(2, total)
    }

    @Test
    fun `依从率百分比边界`() {
        assertEquals(0, DoseTracker.percent(0, 0))
        assertEquals(0, DoseTracker.percent(0, 5))
        assertEquals(33, DoseTracker.percent(1, 3))
        assertEquals(100, DoseTracker.percent(5, 5))
    }
}
