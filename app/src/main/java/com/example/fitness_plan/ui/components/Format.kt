package com.example.fitness_plan.ui.components

import com.example.fitness_plan.data.model.WeightUnit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** 40.0 → "40"，42.5 → "42.5"，按单位换算后保留一位小数。 */
fun formatWeight(kg: Double, unit: WeightUnit): String = formatNumber(unit.fromKg(kg))

fun formatNumber(value: Double): String {
    val rounded = (value * 10).roundToLong() / 10.0
    return if (abs(rounded % 1.0) < 1e-9) rounded.toLong().toString() else rounded.toString()
}

fun formatVolume(kg: Double, unit: WeightUnit): String {
    val v = unit.fromKg(kg)
    return if (v >= 10_000) String.format(Locale.US, "%.1f万", v / 10_000) else v.roundToLong().toString()
}

private val dayFormat = SimpleDateFormat("M月d日", Locale.CHINA)
private val fullDayFormat = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
private val weekdayFormat = SimpleDateFormat("EEE", Locale.CHINA)
private val shortFormat = SimpleDateFormat("M/d", Locale.CHINA)

fun formatSessionDate(millis: Long): String {
    val cal = Calendar.getInstance()
    val thisYear = cal.get(Calendar.YEAR)
    cal.timeInMillis = millis
    val base = if (cal.get(Calendar.YEAR) == thisYear) dayFormat.format(Date(millis)) else fullDayFormat.format(Date(millis))
    return "$base ${weekdayFormat.format(Date(millis))}"
}

fun formatShortDate(millis: Long): String = shortFormat.format(Date(millis))

fun formatFullDate(millis: Long): String = fullDayFormat.format(Date(millis))

/** 相对“今天/昨天/n 天前”的说法，用于概览。 */
fun relativeDay(millis: Long, now: Long = System.currentTimeMillis()): String {
    fun startOfDay(t: Long) = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val days = ((startOfDay(now) - startOfDay(millis)) / 86_400_000L).toInt()
    return when (days) {
        0 -> "今天"
        1 -> "昨天"
        in 2..30 -> "$days 天前"
        else -> formatFullDate(millis)
    }
}
