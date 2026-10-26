package io.github.yu1357465.ironlog

import io.github.yu1357465.ironlog.data.HistoryCsv
import io.github.yu1357465.ironlog.data.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HistoryImportTest {

    private fun day(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    @Test
    fun templateParsesWithoutIssues() {
        val parsed = HistoryCsv.parse(HistoryCsv.template())
        assertTrue(parsed.issues.isEmpty())
        assertEquals(4, parsed.rows.size)
        assertEquals("杠铃平板卧推", parsed.rows[0].exercise)
        assertEquals(MuscleGroup.CHEST, parsed.rows[0].group)
        assertEquals(60.0, parsed.rows[0].weightKg, 0.0)
    }

    @Test
    fun aiPromptContainsExactHeader() {
        assertTrue(HistoryCsv.HEADER in HistoryCsv.aiPrompt)
    }

    @Test
    fun splitHandlesQuotesCommasAndNewlines() {
        val rows = HistoryCsv.split("a,\"b,c\",\"say \"\"hi\"\"\"\r\n\"multi\nline\",x\n\n")
        assertEquals(listOf("a", "b,c", "say \"hi\""), rows[0].second)
        assertEquals(listOf("multi\nline", "x"), rows[1].second)
        assertEquals(2, rows.size)
    }

    @Test
    fun columnsMatchedByHeaderInAnyOrder() {
        val csv = "次数,动作,重量(kg),日期\n8,深蹲,100,2026-10-01"
        val r = HistoryCsv.parse(csv).rows.single()
        assertEquals("深蹲", r.exercise)
        assertEquals(1, r.sets) // 组数缺省为 1
        assertEquals(8, r.reps)
        assertNull(r.group)
    }

    @Test
    fun legacyV2ReportHeaderIsRecognised() {
        val csv = "﻿日期,计划名称,训练日主题,动作名称,动作类型,重量(kg),组数,次数\n2026-01-03 20:15,PPL,推力日,卧推,胸部,40.0,4,12"
        val r = HistoryCsv.parse(csv).rows.single()
        assertEquals("PPL", r.planName)
        assertEquals("推力日", r.dayName)
        assertEquals(MuscleGroup.CHEST, r.group)
        assertEquals(day(2026, 1, 3, 20, 15), r.performedAt)
    }

    @Test
    fun webExportImportsCleanly() {
        // ironlog-web 导出的表头与英文肌群名
        val csv = "date,plan,day,exercise,muscle_group,weight_kg,sets,reps\n" +
            "2026-10-20 18:30,Push Pull Legs,Push,Barbell Bench Press,Chest,62.5,4,8\n" +
            "2026-10-22 18:30,Push Pull Legs,Legs,Barbell Back Squat,Legs & glutes,100,5,5\n"
        val parsed = HistoryCsv.parse(csv)
        assertTrue(parsed.issues.isEmpty())
        assertEquals(listOf(MuscleGroup.CHEST, MuscleGroup.LEGS), parsed.rows.map { it.group })
        assertEquals(62.5, parsed.rows[0].weightKg, 0.0)
        assertEquals("Push", parsed.rows[0].dayName)
    }

    @Test
    fun englishHeadersAndPoundsAreConverted() {
        val r = HistoryCsv.parse("date,exercise,weight (lbs),sets,reps\n2026/10/02,Bench,220.462,3,5").rows.single()
        assertEquals(100.0, r.weightKg, 1e-6)
    }

    @Test
    fun variousDateFormats() {
        assertEquals(day(2026, 10, 5), HistoryCsv.parseDate("2026-10-05"))
        assertEquals(day(2026, 10, 5), HistoryCsv.parseDate("2026/10/05"))
        assertEquals(day(2026, 10, 5), HistoryCsv.parseDate("2026年10月05日"))
        assertEquals(day(2026, 10, 5), HistoryCsv.parseDate("2026.10.05"))
        assertEquals(day(2026, 10, 5, 18, 30), HistoryCsv.parseDate("2026-10-05 18:30"))
        assertNull(HistoryCsv.parseDate("10月5日"))
        assertNull(HistoryCsv.parseDate("2026-13-40"))
    }

    @Test
    fun badRowsReportedWithLineNumbers() {
        val csv = HistoryCsv.HEADER + "\n" +
            "2026-10-01,,,卧推,胸部,60,4,8\n" +
            "昨天,,,卧推,胸部,60,4,8\n" +
            "2026-10-02,,,,胸部,abc,0,-1\n"
        val parsed = HistoryCsv.parse(csv)
        assertEquals(1, parsed.rows.size)
        assertEquals(listOf(3, 4), parsed.issues.map { it.line })
        assertTrue("日期" in parsed.issues[0].message)
        assertTrue("动作为空" in parsed.issues[1].message && "重量" in parsed.issues[1].message)
    }

    @Test
    fun missingRequiredHeaderIsExplained() {
        val parsed = HistoryCsv.parse("名字,公斤\n卧推,60")
        assertTrue(parsed.rows.isEmpty())
        assertTrue("日期" in parsed.issues.single().message)
    }

    @Test
    fun unitsInCellsAreTolerated() {
        val r = HistoryCsv.parse("日期,动作,重量,组数,次数\n2026-10-01,卧推,60kg,4组,8次").rows.single()
        assertEquals(60.0, r.weightKg, 0.0)
        assertEquals(4, r.sets)
        assertEquals(8, r.reps)
    }

    @Test
    fun groupLabelsAndGuesses() {
        assertEquals(MuscleGroup.LEGS, HistoryCsv.groupFromLabel("腿臀"))
        assertEquals(MuscleGroup.LEGS, HistoryCsv.groupFromLabel("腿"))
        assertEquals(MuscleGroup.ARMS, HistoryCsv.groupFromLabel("三头"))
        assertEquals(MuscleGroup.CORE, HistoryCsv.groupFromLabel("abs"))
        assertNull(HistoryCsv.groupFromLabel(""))

        assertEquals(MuscleGroup.CORE, HistoryCsv.guessGroup("悬垂举腿"))
        assertEquals(MuscleGroup.LEGS, HistoryCsv.guessGroup("腿弯举"))
        assertEquals(MuscleGroup.LEGS, HistoryCsv.guessGroup("传统硬拉"))
        assertEquals(MuscleGroup.CHEST, HistoryCsv.guessGroup("双杠臂屈伸"))
        assertEquals(MuscleGroup.ARMS, HistoryCsv.guessGroup("仰卧臂屈伸"))
        assertEquals(MuscleGroup.BACK, HistoryCsv.guessGroup("哑铃耸肩"))
        assertEquals(MuscleGroup.SHOULDERS, HistoryCsv.guessGroup("哑铃侧平举"))
        assertEquals(MuscleGroup.OTHER, HistoryCsv.guessGroup("跳绳"))
    }

    @Test
    fun planGroupsRowsIntoSessionsByDayPlanAndWorkout() {
        val csv = HistoryCsv.HEADER + "\n" +
            "2026-10-01,A,推,卧推,胸部,60,4,8\n" +
            "2026-10-01,A,推,推举,肩部,40,4,8\n" +
            "2026-10-01,A,腿,深蹲,腿臀,100,5,5\n" +
            "2026-10-03,A,推,卧推,胸部,62.5,4,8\n"
        val plan = HistoryCsv.plan(HistoryCsv.parse(csv), existing = emptyList(), knownExercises = setOf("卧推"))
        assertEquals(listOf(2, 1, 1), plan.sessions.map { it.size })
        assertEquals(4, plan.entryCount)
        assertEquals(listOf("推举", "深蹲"), plan.newExercises)
    }

    @Test
    fun sameDayDifferentTimesAreSeparateSessionsInTimeOrder() {
        // 同一套动作同一天练了两次（如补录时日期都记成同一天）
        val csv = HistoryCsv.HEADER + "\n" +
            "2025-12-01 06:50,试动作,胸A,上斜哑铃卧推,胸部,12,3,6\n" +
            "2025-12-01 06:40,试动作,胸A,上斜哑铃卧推,胸部,10,3,6\n" +
            "2025-12-01 06:40,试动作,胸A,蝴蝶机夹胸,胸部,32,3,12\n"
        val plan = HistoryCsv.plan(HistoryCsv.parse(csv), existing = emptyList(), knownExercises = emptySet())
        assertEquals(listOf(2, 1), plan.sessions.map { it.size })
        assertEquals(12.0, plan.sessions.last().single().weightKg, 0.0)
    }

    @Test
    fun reimportingSameDataIsSkipped() {
        val csv = HistoryCsv.HEADER + "\n" +
            "2026-10-01,,,卧推,胸部,60,1,8\n" +
            "2026-10-01,,,卧推,胸部,60,1,8\n" + // 同样的一组记了两行，属于正常情况
            "2026-10-02,,,深蹲,腿臀,100,5,5\n"
        val parsed = HistoryCsv.parse(csv)
        // 数据库里已经有一条同样的卧推（同一天不同时间也算）
        val existing = listOf(HistoryCsv.key(day(2026, 10, 1, 9), "卧推", 60.0, 1, 8))
        val plan = HistoryCsv.plan(parsed, existing, emptySet())
        assertEquals(1, plan.duplicates)
        assertEquals(2, plan.entryCount)
        assertNotNull(plan.sessions.flatten().firstOrNull { it.exercise == "深蹲" })
    }
}
