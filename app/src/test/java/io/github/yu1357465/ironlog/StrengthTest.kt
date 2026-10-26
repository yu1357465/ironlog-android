package io.github.yu1357465.ironlog

import io.github.yu1357465.ironlog.data.Csv
import io.github.yu1357465.ironlog.data.model.MuscleGroup
import io.github.yu1357465.ironlog.data.model.WeightUnit
import io.github.yu1357465.ironlog.domain.Lift
import io.github.yu1357465.ironlog.domain.LiftStandards
import io.github.yu1357465.ironlog.domain.RadarMode
import io.github.yu1357465.ironlog.domain.Strength
import io.github.yu1357465.ironlog.ui.components.formatNumber
import io.github.yu1357465.ironlog.ui.components.formatWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthTest {

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun lift(
        name: String, group: MuscleGroup, weight: Double, reps: Int,
        sets: Int = 3, id: Long = name.hashCode().toLong(), daysAgo: Int = 1,
    ) = Lift(id, name, group, weight, reps, sets, now - daysAgo * day)

    private fun score(vararg lifts: Lift, bw: Double = 70.0, ape: Double = 1.0, mode: RadarMode = RadarMode.OVERALL) =
        Strength.analyze(lifts.toList(), mode, bw, ape, now).scores

    @Test
    fun oneRmFormulasMeetAtTenReps() {
        assertEquals(100.0, Strength.estimate1Rm(100.0, 1), 1e-9)
        val b = Strength.brzycki(50.0, 10)
        val e = Strength.epley(50.0, 10)
        assertTrue(kotlin.math.abs(b - e) / b < 0.01)
        // 超过 10 次走 Epley
        assertEquals(Strength.epley(40.0, 15), Strength.estimate1Rm(40.0, 15), 1e-9)
    }

    @Test
    fun brzyckiGuardsAgainstDivisionByZero() {
        assertEquals(0.0, Strength.brzycki(50.0, 37), 0.0)
        assertEquals(0.0, Strength.brzycki(50.0, 0), 0.0)
    }

    @Test
    fun barbellBenchAtOneAndHalfBodyweightScoresFull() {
        assertEquals(100f, score(lift("杠铃平板卧推", MuscleGroup.CHEST, 105.0, 1)).getValue(MuscleGroup.CHEST), 0.01f)
    }

    @Test
    fun cablePushdownNoLongerInflatesArms() {
        // 旧算法下 20kg×12 绳索下压得 82 分
        val arms = score(lift("绳索下压", MuscleGroup.ARMS, 20.0, 12)).getValue(MuscleGroup.ARMS)
        assertEquals(57.1f, arms, 0.2f)
    }

    @Test
    fun dumbbellWeightsAreJudgedPerHand() {
        // 旧算法下 14kg×10 哑铃推举只有 26 分
        val shoulders = score(lift("哑铃推举", MuscleGroup.SHOULDERS, 14.0, 10)).getValue(MuscleGroup.SHOULDERS)
        assertEquals(63.5f, shoulders, 0.2f)
    }

    @Test
    fun pullUpsCountBodyweight() {
        val back = score(lift("引体向上", MuscleGroup.BACK, 0.0, 8)).getValue(MuscleGroup.BACK)
        assertEquals(82.8f, back, 0.2f)
    }

    @Test
    fun rulesMatchTheRightExercise() {
        // “器械反向飞鸟”含“飞鸟”，但应按肩后束而非胸部夹胸评分
        assertEquals(0.8, LiftStandards.forExercise("器械反向飞鸟", MuscleGroup.SHOULDERS).ratio, 0.0)
        assertEquals(1.1, LiftStandards.forExercise("蝴蝶机夹胸", MuscleGroup.CHEST).ratio, 0.0)
        // “上斜哑铃弯举”不应被当成上斜卧推
        assertEquals(0.38, LiftStandards.forExercise("上斜哑铃弯举", MuscleGroup.ARMS).ratio, 0.0)
        assertEquals(0.45, LiftStandards.forExercise("上斜哑铃卧推", MuscleGroup.CHEST).ratio, 0.0)
        // 手撑高处的上斜俯卧撑负荷比标准俯卧撑轻
        assertEquals(0.45, LiftStandards.forExercise("上斜俯卧撑", MuscleGroup.CHEST).bodyweightFraction, 0.0)
    }

    @Test
    fun fifteenPushUpsAreIntermediateNotFull() {
        // 旧标准下 15 个俯卧撑接近满分
        assertEquals(65.0f, score(lift("自重俯卧撑", MuscleGroup.CHEST, 0.0, 15)).getValue(MuscleGroup.CHEST), 0.2f)
    }

    @Test
    fun assistedPullUpsDoNotCountFullBodyweight() {
        assertEquals(0f, score(lift("辅助引体向上", MuscleGroup.BACK, 0.0, 12)).getValue(MuscleGroup.BACK), 0f)
    }

    @Test
    fun straightArmPulldownUsesItsOwnStandard() {
        // 0.6 倍体重为满分：17kg×8 → Brzycki 21.1 / 42 ≈ 50
        assertEquals(50.3f, score(lift("直臂下拉", MuscleGroup.BACK, 17.0, 8)).getValue(MuscleGroup.BACK), 0.2f)
    }

    @Test
    fun bodyweightCoreScoresByReps() {
        assertEquals(80f, score(lift("悬垂举腿", MuscleGroup.CORE, 0.0, 12)).getValue(MuscleGroup.CORE), 0.01f)
    }

    @Test
    fun noCoreTrainingMeansZeroCore() {
        assertEquals(0f, score(lift("杠铃深蹲", MuscleGroup.LEGS, 140.0, 1)).getValue(MuscleGroup.CORE), 0f)
    }

    @Test
    fun unknownExerciseUsesGroupFallback() {
        // 胸部默认 1.4 倍体重
        assertEquals(100f, score(lift("某种推胸动作X", MuscleGroup.CHEST, 98.0, 1)).getValue(MuscleGroup.CHEST), 0.01f)
    }

    @Test
    fun groupTakesBestExercise() {
        val chest = score(
            lift("杠铃平板卧推", MuscleGroup.CHEST, 70.0, 1),
            lift("器械推胸", MuscleGroup.CHEST, 98.0, 1),
        ).getValue(MuscleGroup.CHEST)
        assertEquals(100f, chest, 0.01f)
    }

    @Test
    fun scoresAreClampedTo100() {
        assertEquals(100f, score(lift("杠铃弯举", MuscleGroup.ARMS, 500.0, 1)).getValue(MuscleGroup.ARMS), 0f)
    }

    @Test
    fun modeFiltersByRepRange() {
        val l = lift("杠铃深蹲", MuscleGroup.LEGS, 60.0, 20)
        assertEquals(0f, score(l, mode = RadarMode.STRENGTH).getValue(MuscleGroup.LEGS), 0f)
        assertTrue(score(l, mode = RadarMode.ENDURANCE).getValue(MuscleGroup.LEGS) > 0f)
    }

    @Test
    fun leverageAffectsPushAndPullButNotSquat() {
        val lifts = arrayOf(
            lift("杠铃平板卧推", MuscleGroup.CHEST, 60.0, 5),
            lift("杠铃划船", MuscleGroup.BACK, 60.0, 5),
            lift("杠铃深蹲", MuscleGroup.LEGS, 80.0, 5),
        )
        val neutral = score(*lifts)
        val longArms = score(*lifts, ape = 1.05)
        assertTrue(longArms.getValue(MuscleGroup.CHEST) > neutral.getValue(MuscleGroup.CHEST))
        assertTrue(longArms.getValue(MuscleGroup.BACK) < neutral.getValue(MuscleGroup.BACK))
        assertEquals(neutral.getValue(MuscleGroup.LEGS), longArms.getValue(MuscleGroup.LEGS), 0f)
    }

    @Test
    fun leverageCorrectionIsCapped() {
        val l = lift("杠铃平板卧推", MuscleGroup.CHEST, 60.0, 5)
        assertEquals(score(l, ape = 1.08).getValue(MuscleGroup.CHEST), score(l, ape = 1.3).getValue(MuscleGroup.CHEST), 0f)
    }

    @Test
    fun oldRecordsIgnoredWhenRecentDataExists() {
        val report = Strength.analyze(
            listOf(
                lift("杠铃平板卧推", MuscleGroup.CHEST, 105.0, 1, id = 1, daysAgo = 200),
                lift("杠铃平板卧推", MuscleGroup.CHEST, 52.5, 1, id = 1, daysAgo = 3),
            ),
            RadarMode.OVERALL, 70.0, 1.0, now,
        )
        assertTrue(report.recentOnly)
        assertEquals(50f, report.scores.getValue(MuscleGroup.CHEST), 0.01f)
    }

    @Test
    fun fallsBackToAllHistoryAfterLongBreak() {
        val report = Strength.analyze(listOf(lift("杠铃平板卧推", MuscleGroup.CHEST, 105.0, 1, daysAgo = 200)), RadarMode.OVERALL, 70.0, 1.0, now)
        assertTrue(!report.recentOnly)
        assertEquals(100f, report.scores.getValue(MuscleGroup.CHEST), 0.01f)
    }

    @Test
    fun missingBodyWeightFallsBackToDefault() {
        val l = lift("杠铃平板卧推", MuscleGroup.CHEST, 60.0, 1)
        assertEquals(score(l, bw = Strength.DEFAULT_BODY_WEIGHT), score(l, bw = 0.0))
    }

    @Test
    fun distributionIsWeightedBySets() {
        val d = Strength.distribution(listOf(
            lift("a", MuscleGroup.CHEST, 50.0, 5, sets = 5),
            lift("b", MuscleGroup.CHEST, 30.0, 10, sets = 3),
            lift("c", MuscleGroup.CORE, 0.0, 20, sets = 2),
        ))
        assertEquals(5, d.strength); assertEquals(3, d.hypertrophy); assertEquals(2, d.endurance)
    }

    @Test
    fun bestLiftsKeepHighestEstimatePerExercise() {
        val report = Strength.analyze(
            listOf(
                lift("杠铃平板卧推", MuscleGroup.CHEST, 60.0, 5, id = 1),
                lift("杠铃平板卧推", MuscleGroup.CHEST, 80.0, 1, id = 1),
                lift("杠铃划船", MuscleGroup.BACK, 50.0, 8, id = 2),
            ),
            RadarMode.OVERALL, 70.0, 1.0, now,
        )
        assertEquals(2, report.bestLifts.size)
        assertEquals(80.0, report.bestLifts.first().estimated1RmKg, 1e-9)
    }
}

class FormatTest {
    @Test
    fun weightsDropTrailingZero() {
        assertEquals("40", formatNumber(40.0))
        assertEquals("42.5", formatNumber(42.5))
        assertEquals("0", formatNumber(0.0))
    }

    @Test
    fun poundsRoundTrip() {
        assertEquals("220.5", formatWeight(100.0, WeightUnit.LBS))
        assertEquals(100.0, WeightUnit.LBS.toKg(WeightUnit.LBS.fromKg(100.0)), 1e-9)
    }

    @Test
    fun csvEscapesSpecialCharacters() {
        assertEquals("plain", Csv.escape("plain"))
        assertEquals("\"a,b\"", Csv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""))
    }
}
