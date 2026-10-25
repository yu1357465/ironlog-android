package com.example.fitness_plan.domain

import com.example.fitness_plan.data.model.MuscleGroup

/** 雷达图的三种数据滤镜。 */
enum class RadarMode(val label: String, val repRange: IntRange) {
    OVERALL("综合", 1..15),
    STRENGTH("力量", 1..6),
    ENDURANCE("耐力", 10..30);

    /** 力量/综合用 Brzycki；高次数下 Brzycki 分母趋近 0，改用 Epley。 */
    fun estimate1Rm(weightKg: Double, reps: Int): Double = when (this) {
        ENDURANCE -> Strength.epley(weightKg, reps)
        else -> Strength.brzycki(weightKg, reps)
    }
}

data class Lift(val exerciseId: Long, val name: String, val group: MuscleGroup, val weightKg: Double, val reps: Int, val sets: Int)

data class EnergyDistribution(val strength: Int, val hypertrophy: Int, val endurance: Int) {
    val total: Int get() = strength + hypertrophy + endurance
    fun fraction(count: Int): Float = if (total == 0) 0f else count.toFloat() / total
}

data class BestLift(val name: String, val group: MuscleGroup, val estimated1RmKg: Double)

data class StrengthReport(
    /** 六大肌群 0–100 分，顺序同 [MuscleGroup.radarGroups]。 */
    val scores: Map<MuscleGroup, Float>,
    val distribution: EnergyDistribution,
    val bestLifts: List<BestLift>,
)

object Strength {

    fun brzycki(weightKg: Double, reps: Int): Double =
        if (reps <= 0 || reps >= 37) 0.0 else weightKg * 36.0 / (37.0 - reps)

    fun epley(weightKg: Double, reps: Int): Double =
        if (reps <= 0) 0.0 else weightKg * (1.0 + reps / 30.0)

    /** 每个肌群拿到满分（100）所需的 1RM 相当于体重的倍数。 */
    private val bodyweightRatio = mapOf(
        MuscleGroup.CHEST to 1.5,
        MuscleGroup.BACK to 1.2,
        MuscleGroup.LEGS to 2.0,
        MuscleGroup.SHOULDERS to 1.0,
        MuscleGroup.ARMS to 0.5,
        MuscleGroup.CORE to 0.4,
    )

    /**
     * 臂展长的人推类动作行程更长（吃亏）、拉类更占优，
     * 因此推类按 apeIndex 放大、拉类按 apeIndex 缩小，抵消杠杆差异。
     */
    private fun leverageCorrection(group: MuscleGroup, apeIndex: Double): Double = when (group) {
        MuscleGroup.CHEST, MuscleGroup.SHOULDERS -> apeIndex
        MuscleGroup.BACK, MuscleGroup.LEGS -> 1.0 / apeIndex
        else -> 1.0
    }

    fun analyze(lifts: List<Lift>, mode: RadarMode, bodyWeightKg: Double, apeIndex: Double): StrengthReport {
        val bw = bodyWeightKg.takeIf { it > 0 } ?: DEFAULT_BODY_WEIGHT
        val raw = mutableMapOf<MuscleGroup, Double>()
        val best1Rm = mutableMapOf<Long, BestLift>()

        lifts.filter { it.reps in mode.repRange && it.weightKg > 0 }.forEach { lift ->
            val rm = mode.estimate1Rm(lift.weightKg, lift.reps)
            if (rm > (best1Rm[lift.exerciseId]?.estimated1RmKg ?: 0.0)) {
                best1Rm[lift.exerciseId] = BestLift(lift.name, lift.group, rm)
            }
            val ratio = bodyweightRatio[lift.group] ?: return@forEach
            val score = rm * leverageCorrection(lift.group, apeIndex) / (bw * ratio) * 100
            raw[lift.group] = maxOf(raw[lift.group] ?: 0.0, score)
        }

        // 深蹲、硬拉、划船对核心也有大量刺激，给核心一定溢出加分
        val coreBonus = ((raw[MuscleGroup.LEGS] ?: 0.0) + (raw[MuscleGroup.BACK] ?: 0.0)) * 0.15
        raw[MuscleGroup.CORE] = (raw[MuscleGroup.CORE] ?: 0.0) + coreBonus

        val scores = MuscleGroup.radarGroups.associateWith { (raw[it] ?: 0.0).coerceIn(0.0, 100.0).toFloat() }

        return StrengthReport(
            scores = scores,
            distribution = distribution(lifts),
            bestLifts = best1Rm.values.sortedByDescending { it.estimated1RmKg }.take(8),
        )
    }

    /** 能量系统分布：按组数加权统计 1–5 / 6–12 / 13+ 次。 */
    fun distribution(lifts: List<Lift>): EnergyDistribution {
        var s = 0; var h = 0; var e = 0
        lifts.forEach {
            val sets = it.sets.coerceAtLeast(1)
            when {
                it.reps <= 0 -> Unit
                it.reps <= 5 -> s += sets
                it.reps <= 12 -> h += sets
                else -> e += sets
            }
        }
        return EnergyDistribution(s, h, e)
    }

    const val DEFAULT_BODY_WEIGHT = 70.0
}
