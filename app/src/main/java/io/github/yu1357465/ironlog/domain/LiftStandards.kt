package io.github.yu1357465.ironlog.domain

import io.github.yu1357465.ironlog.data.model.MuscleGroup

/** 力臂受臂展影响的方向：推类臂长吃亏，拉类臂长占优。 */
enum class Leverage { PUSH, PULL, NONE }

/**
 * 某个动作“满分（100）”对应的水平。
 *
 * - [ratio]：估算 1RM 达到 体重 × ratio 即为满分，大致对应健身房里“进阶”水平。
 *   哑铃动作按单只哑铃重量计。
 * - [bodyweightFraction]：自重动作中实际举起的体重比例（引体≈1.0），
 *   与记录的附加重量相加后再算 1RM。
 * - [repTarget]：纯自重、不负重的动作（如悬垂举腿），按次数评分，达到该次数为满分。
 */
data class LiftStandard(
    val ratio: Double,
    val leverage: Leverage = Leverage.NONE,
    val bodyweightFraction: Double = 0.0,
    val repTarget: Int? = null,
)

/**
 * 根据动作名称识别其评分标准。动作库是用户自定义的，因此按关键词匹配，
 * 认不出的动作退回到所属肌群的默认标准。
 */
object LiftStandards {

    private class Rule(val keywords: List<String>, val standard: (equipment: Equipment) -> LiftStandard)

    private enum class Equipment { BARBELL, DUMBBELL, MACHINE, UNKNOWN }

    private fun equipmentOf(name: String): Equipment = when {
        "哑铃" in name -> Equipment.DUMBBELL
        listOf("绳索", "器械", "坐姿", "史密斯", "拉力器", "龙门架").any { it in name } -> Equipment.MACHINE
        "杠铃" in name -> Equipment.BARBELL
        else -> Equipment.UNKNOWN
    }

    private fun rule(vararg keywords: String, standard: (Equipment) -> LiftStandard) = Rule(keywords.toList(), standard)
    private fun std(ratio: Double, leverage: Leverage = Leverage.NONE) = LiftStandard(ratio, leverage)

    /** 顺序很重要：更具体的关键词放前面（如“罗马尼亚硬拉”先于“硬拉”）。 */
    private val rules = listOf(
        // ---- 腿臀 ----
        rule("罗马尼亚硬拉", "直腿硬拉") { std(if (it == Equipment.DUMBBELL) 0.8 else 1.75, Leverage.PULL) },
        rule("硬拉") { std(if (it == Equipment.DUMBBELL) 1.0 else 2.25, Leverage.PULL) },
        rule("分腿蹲", "箭步蹲", "弓步") { std(if (it == Equipment.DUMBBELL) 0.5 else 1.2) },
        rule("高脚杯深蹲") { std(0.9) },
        rule("深蹲") { std(if (it == Equipment.MACHINE) 2.0 else 1.75) },
        rule("腿举", "倒蹬") { std(3.0) },
        rule("臀推", "臀桥") { std(2.25) },
        rule("腿弯举") { std(1.4) },
        rule("腿屈伸") { std(1.3) },
        rule("腿外展", "腿内收") { std(1.0) },
        rule("提踵") { std(1.5) },
        // ---- 肩后束（须在胸部“飞鸟”之前匹配）----
        rule("反向飞鸟", "后束飞鸟", "俯身飞鸟") { std(if (it == Equipment.MACHINE) 0.8 else 0.25) },
        // ---- 胸 ----
        rule("仰卧臂屈伸", "颅骨粉碎") { std(if (it == Equipment.DUMBBELL) 0.3 else 0.6) },
        rule("双杠臂屈伸", "双杠") { LiftStandard(1.6, Leverage.PUSH, bodyweightFraction = 1.0) },
        // 俯卧撑与卧推同标准；手撑高处的上斜俯卧撑负荷约 45% 体重，脚垫高的下斜约 75%
        rule("上斜俯卧撑") { LiftStandard(1.5, Leverage.PUSH, bodyweightFraction = 0.45) },
        rule("下斜俯卧撑") { LiftStandard(1.5, Leverage.PUSH, bodyweightFraction = 0.75) },
        rule("俯卧撑") { LiftStandard(1.5, Leverage.PUSH, bodyweightFraction = 0.65) },
        rule("夹胸", "飞鸟", "蝴蝶机") { std(if (it == Equipment.DUMBBELL) 0.35 else 1.1) },
        rule("上斜卧推", "上斜哑铃卧推", "上斜杠铃卧推", "上斜推") { std(if (it == Equipment.DUMBBELL) 0.45 else 1.25, Leverage.PUSH) },
        rule("卧推") {
            when (it) {
                Equipment.DUMBBELL -> std(0.55, Leverage.PUSH)
                Equipment.MACHINE -> std(1.4, Leverage.PUSH)
                else -> std(1.5, Leverage.PUSH)
            }
        },
        rule("推胸") { std(1.4, Leverage.PUSH) },
        // ---- 背 ----
        // 辅助引体：记录的“重量”是辅助量而非负重，无法换算，不计入体重以免高估
        rule("辅助引体", "弹力带引体") { std(1.15, Leverage.PULL) },
        rule("引体") { LiftStandard(1.5, Leverage.PULL, bodyweightFraction = 1.0) },
        rule("直臂下拉", "直臂下压") { std(0.6, Leverage.PULL) },
        rule("下拉") { std(1.15, Leverage.PULL) },
        rule("划船") { std(if (it == Equipment.DUMBBELL) 0.65 else 1.2, Leverage.PULL) },
        rule("耸肩") { std(if (it == Equipment.DUMBBELL) 0.7 else 1.5) },
        // ---- 肩 ----
        rule("侧平举", "前平举") { std(if (it == Equipment.MACHINE) 0.35 else 0.22) },
        rule("面拉") { std(0.6, Leverage.PULL) },
        rule("推举", "肩推") {
            when (it) {
                Equipment.DUMBBELL -> std(0.42, Leverage.PUSH)
                Equipment.MACHINE -> std(0.9, Leverage.PUSH)
                else -> std(1.0, Leverage.PUSH)
            }
        },
        // ---- 手臂 ----
        rule("下压") { std(0.7) },
        rule("弯举") { std(if (it == Equipment.DUMBBELL) 0.38 else 0.7) },
        rule("臂屈伸") { std(0.5) },
        // ---- 核心（自重动作按次数）----
        rule("平板支撑") { LiftStandard(0.5, repTarget = 120) },
        rule("举腿") { LiftStandard(0.5, repTarget = 15) },
        rule("卷腹") { LiftStandard(0.6, repTarget = 30) },
        rule("健腹轮") { LiftStandard(0.5, repTarget = 15) },
    )

    /** 认不出动作时，按肌群给一个偏保守的默认标准（杠铃基准，哑铃按单只减半以下）。 */
    private fun fallback(group: MuscleGroup, equipment: Equipment): LiftStandard {
        val (barbell, leverage) = when (group) {
            MuscleGroup.CHEST -> 1.4 to Leverage.PUSH
            MuscleGroup.BACK -> 1.2 to Leverage.PULL
            MuscleGroup.LEGS -> 1.75 to Leverage.NONE
            MuscleGroup.SHOULDERS -> 0.9 to Leverage.PUSH
            MuscleGroup.ARMS -> 0.7 to Leverage.NONE
            MuscleGroup.CORE -> 0.5 to Leverage.NONE
            MuscleGroup.OTHER -> 1.0 to Leverage.NONE
        }
        val ratio = if (equipment == Equipment.DUMBBELL) barbell * 0.4 else barbell
        return LiftStandard(ratio, leverage, repTarget = if (group == MuscleGroup.CORE) 20 else null)
    }

    fun forExercise(name: String, group: MuscleGroup): LiftStandard {
        val equipment = equipmentOf(name)
        return rules.firstOrNull { r -> r.keywords.any { it in name } }?.standard?.invoke(equipment)
            ?: fallback(group, equipment)
    }
}
