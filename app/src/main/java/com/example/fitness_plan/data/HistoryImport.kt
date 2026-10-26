package com.example.fitness_plan.data

import com.example.fitness_plan.data.model.MuscleGroup
import com.example.fitness_plan.data.model.WeightUnit
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 导入文件中的一条训练记录（一个动作）。 */
data class ImportRow(
    val line: Int,
    val performedAt: Long,
    val planName: String?,
    val dayName: String?,
    val exercise: String,
    val group: MuscleGroup?,
    val weightKg: Double,
    val sets: Int,
    val reps: Int,
)

data class ImportIssue(val line: Int, val message: String)

data class ParsedImport(val rows: List<ImportRow>, val issues: List<ImportIssue>)

/** 写入前的导入计划：按天+计划+训练日分成若干次训练，并剔除已存在的记录。 */
data class ImportPlan(
    val sessions: List<List<ImportRow>>,
    val duplicates: Int,
    val newExercises: List<String>,
    val issues: List<ImportIssue>,
) {
    val entryCount: Int get() = sessions.sumOf { it.size }
}

/**
 * 训练记录 CSV 的导入格式。与“导出训练报表”的列完全相同，导出的报表可直接导回。
 * 按表头识别列（顺序不限，支持中英文别名及 V2 旧版报表的列名）。
 */
object HistoryCsv {

    const val HEADER = "日期,计划,训练日,动作,肌群,重量(kg),组数,次数"

    private enum class Column(vararg val aliases: String) {
        DATE("日期", "date", "时间", "训练日期"),
        PLAN("计划", "计划名称", "plan"),
        DAY("训练日", "训练日主题", "训练日名称", "day", "workout"),
        EXERCISE("动作", "动作名称", "exercise", "name"),
        GROUP("肌群", "动作类型", "部位", "目标肌群", "muscle", "category"),
        WEIGHT("重量", "weight"),
        SETS("组数", "sets"),
        REPS("次数", "reps"),
    }

    // ---------------- 模板与提示词 ----------------

    fun template(): String = buildString {
        append(HEADER).append('\n')
        append("2026-10-20,经典推拉腿,推力日,杠铃平板卧推,胸部,60,4,8\n")
        append("2026-10-20,经典推拉腿,推力日,哑铃推举,肩部,16,3,10\n")
        append("2026-10-22,经典推拉腿,拉力日,引体向上,背部,0,4,8\n")
        append("2026-10-22,经典推拉腿,拉力日,杠铃划船,背部,50,4,10\n")
    }

    val aiPrompt: String = """
        请把我下面提供的健身训练记录转换成 CSV，以便导入我的健身 App。要求：

        1. 第一行是表头，必须完全是：$HEADER
        2. 每一行代表一次训练中的一个动作。同一天练了 5 个动作就写 5 行。
        3. 日期：格式 yyyy-MM-dd（如 2026-10-20）；不确定年份时按最近的年份推断。
        4. 计划、训练日：可留空。训练日是当天练的内容，如“推力日”“腿部日”“上肢”。
        5. 动作：使用常见的中文动作名，如“杠铃平板卧推”“哑铃推举”“高位下拉”；同一个动作在全表中名称保持一致。
        6. 肌群：只能是 胸部、背部、腿臀、肩部、手臂、核心、其他 之一。
        7. 重量：纯数字，单位 kg（原始数据是磅的请换算成 kg，保留一位小数）；自重动作填 0；哑铃填单只的重量。
        8. 组数、次数：正整数。如果原始记录里每组次数或重量不同，可以拆成多行，每行组数填 1；
           也可以合并为一行，次数填大多数组的次数。
        9. 字段里如果含逗号，用英文双引号包起来。
        10. 只输出 CSV 内容本身，不要加任何解释，也不要放在代码块里。

        示例：
        ${template().trimEnd().replace("\n", "\n        ")}

        下面是我的原始记录：
    """.trimIndent() + "\n"

    // ---------------- 解析 ----------------

    /** 符合 RFC 4180 的 CSV 拆分：支持引号、引号内逗号/换行、"" 转义。返回 (起始行号, 字段) 列表。 */
    fun split(text: String): List<Pair<Int, List<String>>> {
        val rows = mutableListOf<Pair<Int, List<String>>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var line = 1
        var rowStart = 1
        var i = 0
        val s = text.removePrefix("﻿")
        fun endRow() {
            fields.add(field.toString()); field.clear()
            if (fields.any { it.isNotBlank() }) rows.add(rowStart to fields)
            fields = mutableListOf()
        }
        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes && c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                // 逗号分隔；也接受制表符（从表格软件直接复制出来的内容）
                !inQuotes && (c == ',' || c == '\t') -> { fields.add(field.toString()); field.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                    endRow(); line++; rowStart = line
                }
                else -> { if (c == '\n') line++; field.append(c) }
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) endRow()
        return rows
    }

    fun parse(text: String): ParsedImport {
        val table = split(text)
        if (table.isEmpty()) return ParsedImport(emptyList(), listOf(ImportIssue(0, "文件是空的")))

        val (headerLine, header) = table.first()
        val columns = mutableMapOf<Column, Int>()
        header.forEachIndexed { idx, raw ->
            val name = raw.trim().lowercase(Locale.ROOT)
            Column.entries.firstOrNull { col -> col !in columns && col.aliases.any { name.startsWith(it.lowercase(Locale.ROOT)) } }
                ?.let { columns[it] = idx }
        }
        val missing = listOf(Column.DATE, Column.EXERCISE, Column.WEIGHT, Column.REPS).filter { it !in columns }
        if (missing.isNotEmpty()) {
            return ParsedImport(
                emptyList(),
                listOf(ImportIssue(headerLine, "第一行表头缺少：" + missing.joinToString("、") { it.aliases.first() } + "。正确表头：$HEADER")),
            )
        }
        val weightUnit = header[columns.getValue(Column.WEIGHT)].lowercase(Locale.ROOT)
            .let { if ("lb" in it || "磅" in it) WeightUnit.LBS else WeightUnit.KG }

        val rows = mutableListOf<ImportRow>()
        val issues = mutableListOf<ImportIssue>()
        for ((line, cells) in table.drop(1)) {
            fun cell(c: Column) = columns[c]?.let { cells.getOrNull(it) }?.trim().orEmpty()
            val problems = mutableListOf<String>()

            val date = parseDate(cell(Column.DATE))
            if (date == null) problems += "日期「${cell(Column.DATE)}」无法识别"
            val exercise = cell(Column.EXERCISE)
            if (exercise.isEmpty()) problems += "动作为空"
            val weight = parseNumber(cell(Column.WEIGHT))
            if (weight == null || weight < 0) problems += "重量「${cell(Column.WEIGHT)}」不是有效数字"
            val reps = parseNumber(cell(Column.REPS))?.toInt()
            if (reps == null || reps <= 0) problems += "次数「${cell(Column.REPS)}」不是正整数"
            val setsText = cell(Column.SETS)
            val sets = if (setsText.isEmpty()) 1 else parseNumber(setsText)?.toInt()
            if (sets == null || sets <= 0) problems += "组数「$setsText」不是正整数"

            if (problems.isNotEmpty()) {
                issues += ImportIssue(line, problems.joinToString("；"))
                continue
            }
            rows += ImportRow(
                line = line,
                performedAt = date!!,
                planName = cell(Column.PLAN).ifEmpty { null },
                dayName = cell(Column.DAY).ifEmpty { null },
                exercise = exercise,
                group = groupFromLabel(cell(Column.GROUP)),
                weightKg = weightUnit.toKg(weight!!),
                sets = sets!!,
                reps = reps!!,
            )
        }
        return ParsedImport(rows, issues)
    }

    private val datePatterns = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd",
        "yyyy/MM/dd HH:mm", "yyyy/MM/dd", "yyyy.MM.dd", "yyyy年MM月dd日 HH:mm", "yyyy年MM月dd日",
    )

    /** 解析日期；只有日期没有时间的，记为当天中午。 */
    fun parseDate(text: String): Long? {
        val t = text.trim()
        if (t.isEmpty()) return null
        for (pattern in datePatterns) {
            val fmt = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            val pos = ParsePosition(0)
            val date = fmt.parse(t, pos) ?: continue
            if (pos.index != t.length) continue
            val cal = Calendar.getInstance().apply { time = date }
            if (cal.get(Calendar.YEAR) !in 1990..2100) return null
            if (!pattern.contains("HH")) cal.set(Calendar.HOUR_OF_DAY, 12)
            return cal.timeInMillis
        }
        return null
    }

    private fun parseNumber(text: String): Double? =
        text.trim().lowercase(Locale.ROOT).removeSuffix("kg").removeSuffix("lbs").removeSuffix("lb")
            .removeSuffix("次").removeSuffix("组").trim().toDoubleOrNull()

    fun groupFromLabel(label: String): MuscleGroup? {
        val l = label.trim().lowercase(Locale.ROOT)
        if (l.isEmpty()) return null
        MuscleGroup.entries.firstOrNull { it.label == label.trim() || it.name.lowercase(Locale.ROOT) == l }?.let { return it }
        return when {
            "胸" in l || "chest" in l -> MuscleGroup.CHEST
            "背" in l || "back" in l -> MuscleGroup.BACK
            "腿" in l || "臀" in l || "leg" in l || "glute" in l -> MuscleGroup.LEGS
            "肩" in l || "shoulder" in l -> MuscleGroup.SHOULDERS
            "臂" in l || "二头" in l || "三头" in l || "arm" in l -> MuscleGroup.ARMS
            "核心" in l || "腹" in l || "core" in l || "abs" in l -> MuscleGroup.CORE
            else -> MuscleGroup.OTHER
        }
    }

    /** 没写肌群时按动作名猜测。 */
    fun guessGroup(exercise: String): MuscleGroup {
        fun has(vararg words: String) = words.any { it in exercise }
        // 顺序有讲究：“悬垂举腿”含“腿”但属于核心，“腿弯举”含“弯举”但属于腿
        return when {
            has("卧推", "推胸", "夹胸", "飞鸟", "双杠", "俯卧撑") -> MuscleGroup.CHEST
            has("卷腹", "举腿", "平板支撑", "健腹轮", "腹") -> MuscleGroup.CORE
            has("蹲", "硬拉", "腿", "臀", "提踵", "弓步") -> MuscleGroup.LEGS
            has("引体", "下拉", "划船", "耸肩") -> MuscleGroup.BACK
            has("推举", "肩推", "平举", "面拉", "肩") -> MuscleGroup.SHOULDERS
            has("弯举", "下压", "臂屈伸", "二头", "三头") -> MuscleGroup.ARMS
            else -> MuscleGroup.OTHER
        }
    }

    // ---------------- 导入计划 ----------------

    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** 判断“同一条记录”的键：同一天、同一动作、同样的重量/组数/次数。 */
    fun key(performedAt: Long, exercise: String, weightKg: Double, sets: Int, reps: Int): String =
        "${startOfDay(performedAt)}|${exercise.trim()}|${Math.round(weightKg * 10)}|$sets|$reps"

    /**
     * @param existing 数据库中已有记录的键（可重复，代表多条一样的记录）
     * @param knownExercises 动作库中已有的动作名
     */
    fun plan(parsed: ParsedImport, existing: List<String>, knownExercises: Set<String>): ImportPlan {
        // 已有记录按次数抵消：已存在 2 条一样的，文件里同样的前 2 条视为重复
        val remaining = existing.groupingBy { it }.eachCount().toMutableMap()
        var duplicates = 0
        val fresh = parsed.rows.filter { r ->
            val k = key(r.performedAt, r.exercise, r.weightKg, r.sets, r.reps)
            val left = remaining[k] ?: 0
            if (left > 0) { remaining[k] = left - 1; duplicates++; false } else true
        }
        val sessions = fresh
            // 按精确时间分组：同一天里时间不同的记录是不同的训练（导出的报表中同一次训练时间相同）；
            // 只写了日期的行都记为当天中午，自然合并为一次
            .groupBy { Triple(it.performedAt, it.planName, it.dayName) }
            .toSortedMap(compareBy({ it.first }, { it.second.orEmpty() }, { it.third.orEmpty() }))
            .values.map { it.sortedBy { r -> r.line } }
        val newExercises = fresh.map { it.exercise.trim() }.distinct().filter { it !in knownExercises }
        return ImportPlan(sessions, duplicates, newExercises, parsed.issues)
    }
}
