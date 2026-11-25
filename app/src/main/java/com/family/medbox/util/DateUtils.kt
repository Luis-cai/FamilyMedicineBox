package com.family.medbox.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 日期工具：统一使用 yyyy-MM-dd 存储，展示时使用中文格式。
 */
object DateUtils {

    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val compactFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private val cnFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
    private val monthDayFormatter = DateTimeFormatter.ofPattern("M月d日")

    fun today(): LocalDate = LocalDate.now()

    fun parseIso(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(value.trim(), isoFormatter) }.getOrNull()
    }

    fun toIso(date: LocalDate): String = date.format(isoFormatter)

    fun displayDate(value: String?): String =
        parseIso(value)?.format(cnFormatter) ?: "未填写"

    fun displayCompact(value: String?): String =
        parseIso(value)?.format(compactFormatter) ?: "未填写"

    fun monthDay(value: String?): String = parseIso(value)?.format(monthDayFormatter) ?: ""

    /** 距今天还有多少天，已过期为负数 */
    fun daysLeft(value: String?): Long? {
        val date = parseIso(value) ?: return null
        return ChronoUnit.DAYS.between(today(), date)
    }

    fun humanizeDays(days: Long): String = when {
        days < -1L -> "已过期 ${-days} 天"
        days == -1L -> "昨天已过期"
        days == 0L -> "今天到期"
        days == 1L -> "明天到期"
        days <= 31L -> "还有 $days 天到期"
        days <= 365L -> "还有 ${days / 30} 个月到期"
        else -> "有效期充足"
    }

    fun chatTimeStamp(): String {
        val now = java.time.LocalDateTime.now()
        return now.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    /** 字段级 LWW 用的写入时间：yyyy-MM-ddTHH:mm:ss.SSS，与本机时区一致。 */
    fun timestamp(): String =
        java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"))
}
