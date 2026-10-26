package com.example.fitness_plan.domain

import com.example.fitness_plan.data.model.MuscleGroup

/** 雷达图的三种数据滤镜：只统计对应次数区间的组。 */
enum class RadarMode(val label: String, val repRange: IntRange) {
    OVERALL("综合", 1..15),
    STRENGTH("力量", 1..6),
    ENDURANCE("耐力", 10..30),
}

data class Lift(
    val exerciseId: Long,
    val name: String,
    val group: MuscleGroup,
    val weightKg: Double,
    val reps: Int,
    val sets: Int,
    val performedAt: Long = 0L,
)

data class EnergyDistribution(val strength: Int, val hypertrophy: Int, val endurance: Int) {
    val total: Int get() = strength + hypertrophy + endurance
    fun fraction(count: Int): Float = if (total == 0) 0f else count.toFloat() / total
}

data class BestLift(val name: String, val group: MuscleGroup, val estimated1RmKg: Double)

data class StrengthReport(
    /** 六大肌群 0–100 分；没有数据的肌群为 0。 */
    val scores: Map<MuscleGroup, Float>,
    val distribution: EnergyDistribution,
    val bestLifts: List<BestLift>,
    /** 评分是否只用了最近 [Strength.RECENT_DAYS] 天的数据（否则说明近期没练，退回全部历史）。 */
    val recentOnly: Boolean,
)

object Strength {

    const val DEFAULT_BODY_WEIGHT = 70.0
    const val RECENT_DAYS = 90
    private const val DAY_MS = 86_400_000L

    fun brzycki(weightKg: Double, reps: Int): Double =
        if (reps <= 0 || reps >= 37) 0.0 else weightKg * 36.0 / (37.0 - reps)

    fun epley(weightKg: Double, reps: Int): Double =
        if (reps <= 0) 0.0 else if (reps == 1) weightKg else weightKg * (1.0 + reps / 30.0)

    /**
     * 估算 1RM：10 次以内 Brzycki 更准；超过 10 次 Brzycki 迅速高估（分母趋近 0），改用 Epley。
     * 两个公式在 10 次处几乎相等，衔接平滑。
     */
    fun estimate1Rm(weightKg: Double, reps: Int): Double =
        if (reps <= 10) brzycki(weightKg, reps) else epley(weightKg, reps)

    /**
     * 臂展/身高（Ape Index）对力臂的修正：推类按比例放大、拉类按比例缩小。
     * 限制在 ±8% 以内，避免录入误差把评分拉偏。
     */
    private fun leverageFactor(leverage: Leverage, apeIndex: Double): Double {
        val ape = apeIndex.coerceIn(0.92, 1.08)
        return when (leverage) {
            Leverage.PUSH -> ape
            Leverage.PULL -> 1.0 / ape
            Leverage.NONE -> 1.0
        }
    }

    /** 单组训练的得分（0–100+，未截断）。 */
    fun scoreSet(lift: Lift, bodyWeightKg: Double, apeIndex: Double): Double {
        val standard = LiftStandards.forExercise(lift.name, lift.group)
        if (lift.weightKg <= 0 && standard.bodyweightFraction == 0.0) {
            // 纯自重动作：按次数评分
            val target = standard.repTarget ?: return 0.0
            return lift.reps.toDouble() / target * 100
        }
        val load = lift.weightKg + bodyWeightKg * standard.bodyweightFraction
        val rm = estimate1Rm(load, lift.reps) * leverageFactor(standard.leverage, apeIndex)
        return rm / (bodyWeightKg * standard.ratio) * 100
    }

    fun analyze(
        lifts: List<Lift>,
        mode: RadarMode,
        bodyWeightKg: Double,
        apeIndex: Double,
        now: Long = System.currentTimeMillis(),
    ): StrengthReport {
        val bw = bodyWeightKg.takeIf { it > 0 } ?: DEFAULT_BODY_WEIGHT
        val recent = lifts.filter { it.performedAt >= now - RECENT_DAYS * DAY_MS }
        val source = recent.ifEmpty { lifts }
        val inMode = source.filter { it.reps in mode.repRange }

        // 每个动作取最佳一组
        val bestPerExercise = inMode.groupBy { it.exerciseId }.mapValues { (_, sets) ->
            sets.maxOf { scoreSet(it, bw, apeIndex) }
        }
        val groupOf = inMode.associate { it.exerciseId to it.group }
        val scores = MuscleGroup.radarGroups.associateWith { g ->
            // 肌群得分 = 该肌群表现最好的动作的得分
            val best = bestPerExercise.filterKeys { groupOf[it] == g }.values.maxOrNull() ?: 0.0
            best.coerceIn(0.0, 100.0).toFloat()
        }

        val best1Rm = source.filter { it.reps in 1..15 && it.weightKg > 0 }
            .groupBy { it.exerciseId }
            .map { (_, sets) ->
                val top = sets.maxBy { estimate1Rm(it.weightKg, it.reps) }
                BestLift(top.name, top.group, estimate1Rm(top.weightKg, top.reps))
            }
            .sortedByDescending { it.estimated1RmKg }
            .take(8)

        return StrengthReport(
            scores = scores,
            distribution = distribution(source),
            bestLifts = best1Rm,
            recentOnly = recent.isNotEmpty(),
        )
    }

    /** 训练结构：按组数统计 1–5 / 6–12 / 13+ 次。 */
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
}
