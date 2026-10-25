package com.example.fitness_plan.data.model

/** 肌群分类。数据库里存 [name]，界面显示 [label]。 */
enum class MuscleGroup(val label: String) {
    CHEST("胸部"),
    BACK("背部"),
    LEGS("腿臀"),
    SHOULDERS("肩部"),
    ARMS("手臂"),
    CORE("核心"),
    OTHER("其他");

    companion object {
        /** 参与力量雷达图的六大肌群（不含“其他”）。 */
        val radarGroups = listOf(CHEST, BACK, LEGS, SHOULDERS, ARMS, CORE)

        fun fromName(name: String?): MuscleGroup =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

enum class WeightUnit(val label: String) {
    KG("kg"),
    LBS("lbs");

    fun fromKg(kg: Double): Double = if (this == LBS) kg * LBS_PER_KG else kg
    fun toKg(value: Double): Double = if (this == LBS) value / LBS_PER_KG else value

    companion object {
        const val LBS_PER_KG = 2.20462
    }
}
