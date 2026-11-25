package com.family.medbox.util

import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.StockStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineStatusTest {

    private val today = DateUtils.today()

    private fun medicine(expiry: String?, quantity: Int = 10) = Medicine(
        id = 1L,
        name = "布洛芬",
        ingredient = "布洛芬",
        quantity = quantity,
        unit = "盒",
        expiryDate = expiry
    )

    @Test
    fun `有效期与距离天数的换算`() {
        assertEquals(3L, DateUtils.daysLeft(DateUtils.toIso(today.plusDays(3))))
        assertEquals(-2L, DateUtils.daysLeft(DateUtils.toIso(today.minusDays(2))))
        assertNull(DateUtils.daysLeft("not-a-date"))
        assertNull(DateUtils.daysLeft(null))
    }

    @Test
    fun `临期文案覆盖边界值`() {
        assertEquals("已过期 3 天", DateUtils.humanizeDays(-3))
        assertEquals("昨天已过期", DateUtils.humanizeDays(-1))
        assertEquals("今天到期", DateUtils.humanizeDays(0))
        assertEquals("明天到期", DateUtils.humanizeDays(1))
        assertEquals("还有 31 天到期", DateUtils.humanizeDays(31))
        assertEquals("还有 1 个月到期", DateUtils.humanizeDays(40))
        assertEquals("有效期充足", DateUtils.humanizeDays(400))
    }

    @Test
    fun `状态按 30 天分界`() {
        assertEquals(StockStatus.EXPIRED, medicine(DateUtils.toIso(today.minusDays(1))).status)
        assertEquals(StockStatus.EXPIRING, medicine(DateUtils.toIso(today)).status)
        assertEquals(StockStatus.EXPIRING, medicine(DateUtils.toIso(today.plusDays(30))).status)
        assertEquals(StockStatus.NORMAL, medicine(DateUtils.toIso(today.plusDays(31))).status)
        assertEquals(StockStatus.NORMAL, medicine(null).status)
    }

    @Test
    fun `过期药不可用`() {
        assertFalse(medicine(DateUtils.toIso(today.minusDays(1))).usable)
        assertTrue(medicine(DateUtils.toIso(today.plusDays(60))).usable)
    }
}
