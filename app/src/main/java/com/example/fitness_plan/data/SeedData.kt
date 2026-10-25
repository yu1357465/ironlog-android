package com.example.fitness_plan.data

import com.example.fitness_plan.data.model.MuscleGroup
import com.example.fitness_plan.data.model.MuscleGroup.ARMS
import com.example.fitness_plan.data.model.MuscleGroup.BACK
import com.example.fitness_plan.data.model.MuscleGroup.CHEST
import com.example.fitness_plan.data.model.MuscleGroup.CORE
import com.example.fitness_plan.data.model.MuscleGroup.LEGS
import com.example.fitness_plan.data.model.MuscleGroup.SHOULDERS

/** 首次安装时写入的动作库与一套推拉腿计划。 */
object SeedData {

    data class TemplateItem(val name: String, val sets: Int, val reps: Int, val weightKg: Double)

    const val PLAN_NAME = "经典推拉腿 (PPL)"

    val exercises: List<Pair<String, MuscleGroup>> = listOf(
        "杠铃平板卧推" to CHEST, "上斜哑铃卧推" to CHEST, "器械推胸" to CHEST,
        "绳索夹胸" to CHEST, "双杠臂屈伸" to CHEST,
        "引体向上" to BACK, "高位下拉" to BACK, "杠铃划船" to BACK,
        "坐姿划船" to BACK, "单臂哑铃划船" to BACK,
        "杠铃深蹲" to LEGS, "传统硬拉" to LEGS, "罗马尼亚硬拉" to LEGS,
        "腿举" to LEGS, "保加利亚分腿蹲" to LEGS, "杠铃臀推" to LEGS,
        "哑铃推举" to SHOULDERS, "杠铃推举" to SHOULDERS,
        "哑铃侧平举" to SHOULDERS, "绳索面拉" to SHOULDERS,
        "杠铃弯举" to ARMS, "哑铃锤式弯举" to ARMS, "绳索下压" to ARMS, "仰卧臂屈伸" to ARMS,
        "平板支撑" to CORE, "悬垂举腿" to CORE, "卷腹" to CORE,
    )

    val days: List<Pair<String, List<TemplateItem>>> = listOf(
        "推力日" to listOf(
            TemplateItem("杠铃平板卧推", 4, 8, 40.0),
            TemplateItem("上斜哑铃卧推", 3, 10, 16.0),
            TemplateItem("哑铃推举", 4, 10, 14.0),
            TemplateItem("哑铃侧平举", 3, 15, 6.0),
            TemplateItem("绳索下压", 3, 12, 20.0),
        ),
        "拉力日" to listOf(
            TemplateItem("引体向上", 4, 8, 0.0),
            TemplateItem("杠铃划船", 4, 8, 40.0),
            TemplateItem("高位下拉", 3, 10, 35.0),
            TemplateItem("绳索面拉", 3, 15, 15.0),
            TemplateItem("杠铃弯举", 3, 10, 20.0),
        ),
        "腿部日" to listOf(
            TemplateItem("杠铃深蹲", 5, 5, 60.0),
            TemplateItem("罗马尼亚硬拉", 3, 8, 50.0),
            TemplateItem("腿举", 3, 12, 80.0),
            TemplateItem("杠铃臀推", 3, 10, 60.0),
            TemplateItem("悬垂举腿", 3, 12, 0.0),
        ),
    )
}
