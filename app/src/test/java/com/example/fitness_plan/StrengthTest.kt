package com.example.fitness_plan

import com.example.fitness_plan.data.Csv
import com.example.fitness_plan.data.model.MuscleGroup
import com.example.fitness_plan.data.model.WeightUnit
import com.example.fitness_plan.domain.Lift
import com.example.fitness_plan.domain.RadarMode
import com.example.fitness_plan.domain.Strength
import com.example.fitness_plan.ui.components.formatNumber
import com.example.fitness_plan.ui.components.formatWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthTest {

    private fun lift(group: MuscleGroup, weight: Double, reps: Int, sets: Int = 3, id: Long = group.ordinal.toLong()) =
        Lift(id, group.label, group, weight, reps, sets)

    @Test
    fun brzyckiOneRepIsTheWeightItself() {
        assertEquals(100.0, Strength.brzycki(100.0, 1), 1e-9)
    }

    @Test
    fun brzyckiGuardsAgainstDivisionByZero() {
        assertEquals(0.0, Strength.brzycki(50.0, 37), 0.0)
        assertEquals(0.0, Strength.brzycki(50.0, 0), 0.0)
    }

    @Test
    fun epleyMatchesFormula() {
        assertEquals(60.0, Strength.epley(40.0, 15), 1e-9)
    }

    @Test
    fun benchAtOneAndHalfBodyweightScoresFull() {
        val report = Strength.analyze(listOf(lift(MuscleGroup.CHEST, 120.0, 1)), RadarMode.OVERALL, 80.0, 1.0)
        assertEquals(100f, report.scores.getValue(MuscleGroup.CHEST), 0.01f)
    }

    @Test
    fun scoresAreClampedTo100() {
        val report = Strength.analyze(listOf(lift(MuscleGroup.ARMS, 500.0, 1)), RadarMode.OVERALL, 70.0, 1.0)
        assertEquals(100f, report.scores.getValue(MuscleGroup.ARMS), 0f)
    }

    @Test
    fun modeFiltersByRepRange() {
        val lifts = listOf(lift(MuscleGroup.LEGS, 100.0, 20))
        assertEquals(0f, Strength.analyze(lifts, RadarMode.STRENGTH, 70.0, 1.0).scores.getValue(MuscleGroup.LEGS), 0f)
        assertTrue(Strength.analyze(lifts, RadarMode.ENDURANCE, 70.0, 1.0).scores.getValue(MuscleGroup.LEGS) > 0f)
    }

    @Test
    fun longArmsBoostPushAndReducePull() {
        val lifts = listOf(lift(MuscleGroup.CHEST, 60.0, 5), lift(MuscleGroup.BACK, 60.0, 5))
        val neutral = Strength.analyze(lifts, RadarMode.OVERALL, 70.0, 1.0).scores
        val longArms = Strength.analyze(lifts, RadarMode.OVERALL, 70.0, 1.05).scores
        assertTrue(longArms.getValue(MuscleGroup.CHEST) > neutral.getValue(MuscleGroup.CHEST))
        assertTrue(longArms.getValue(MuscleGroup.BACK) < neutral.getValue(MuscleGroup.BACK))
    }

    @Test
    fun legsAndBackSpillOverIntoCore() {
        val report = Strength.analyze(listOf(lift(MuscleGroup.LEGS, 140.0, 1)), RadarMode.OVERALL, 70.0, 1.0)
        assertEquals(15f, report.scores.getValue(MuscleGroup.CORE), 0.01f)
    }

    @Test
    fun missingBodyWeightFallsBackToDefault() {
        val a = Strength.analyze(listOf(lift(MuscleGroup.CHEST, 60.0, 1)), RadarMode.OVERALL, 0.0, 1.0)
        val b = Strength.analyze(listOf(lift(MuscleGroup.CHEST, 60.0, 1)), RadarMode.OVERALL, Strength.DEFAULT_BODY_WEIGHT, 1.0)
        assertEquals(b.scores, a.scores)
    }

    @Test
    fun distributionIsWeightedBySets() {
        val d = Strength.distribution(listOf(lift(MuscleGroup.CHEST, 50.0, 5, sets = 5), lift(MuscleGroup.CHEST, 30.0, 10, sets = 3), lift(MuscleGroup.CORE, 0.0, 20, sets = 2)))
        assertEquals(5, d.strength)
        assertEquals(3, d.hypertrophy)
        assertEquals(2, d.endurance)
        assertEquals(0.5f, d.fraction(d.strength), 1e-6f)
    }

    @Test
    fun bestLiftsKeepHighestEstimatePerExercise() {
        val report = Strength.analyze(
            listOf(lift(MuscleGroup.CHEST, 60.0, 5, id = 1), lift(MuscleGroup.CHEST, 80.0, 1, id = 1), lift(MuscleGroup.BACK, 50.0, 8, id = 2)),
            RadarMode.OVERALL, 70.0, 1.0,
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
