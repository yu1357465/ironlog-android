package com.example.fitness_plan.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.fitness_plan.data.model.MuscleGroup

/** 动作库：所有其他表通过 exerciseId 引用这里，改名只需改一处。 */
@Entity(tableName = "exercises", indices = [Index(value = ["name"], unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: MuscleGroup,
    /** 从动作库“删除”只做归档，历史记录仍可正常显示。 */
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = false,
    /** 该计划当前进行到哪一个训练日；完成训练后自动推进到下一天。 */
    val currentDayId: Long? = null,
)

@Entity(
    tableName = "plan_days",
    foreignKeys = [ForeignKey(
        entity = PlanEntity::class,
        parentColumns = ["id"],
        childColumns = ["planId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("planId")],
)
data class PlanDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val name: String,
    val sortOrder: Int = 0,
)

/** 训练日模板里的一个动作及其默认组数/次数/重量。 */
@Entity(
    tableName = "plan_day_exercises",
    foreignKeys = [
        ForeignKey(
            entity = PlanDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
        ),
    ],
    indices = [Index("dayId"), Index("exerciseId")],
)
data class PlanDayExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val exerciseId: Long,
    val sets: Int,
    val reps: Int,
    val weightKg: Double,
    val sortOrder: Int = 0,
)

/** 今天训练台上的动作卡片。重量统一以 kg 存储，显示时再换算。 */
@Entity(
    tableName = "active_exercises",
    foreignKeys = [ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
    )],
    indices = [Index("exerciseId")],
)
data class ActiveExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val weightKg: Double,
    val sets: Int,
    val reps: Int,
    val completed: Boolean = false,
    /** true：属于当前训练日模板；false：仅本次临时加入。 */
    val inPlan: Boolean = true,
    val sortOrder: Int = 0,
)

/** 一次完成的训练。 */
@Entity(tableName = "workout_sessions", indices = [Index("performedAt")])
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val performedAt: Long,
    /** 训练时所用计划/训练日名称的快照，计划改名或删除后依然可追溯。 */
    val planName: String?,
    val dayName: String?,
)

@Entity(
    tableName = "workout_entries",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class WorkoutEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val weightKg: Double,
    val sets: Int,
    val reps: Int,
    val sortOrder: Int = 0,
)

// ---------- 查询结果 ----------

data class ActiveExerciseDetail(
    @Embedded val item: ActiveExerciseEntity,
    @ColumnInfo(name = "exerciseName") val name: String,
    @ColumnInfo(name = "exerciseGroup") val muscleGroup: MuscleGroup,
)

data class PlanDayExerciseDetail(
    @Embedded val item: PlanDayExerciseEntity,
    @ColumnInfo(name = "exerciseName") val name: String,
    @ColumnInfo(name = "exerciseGroup") val muscleGroup: MuscleGroup,
)

data class EntryDetail(
    @Embedded val entry: WorkoutEntryEntity,
    @ColumnInfo(name = "exerciseName") val name: String,
    @ColumnInfo(name = "exerciseGroup") val muscleGroup: MuscleGroup,
    val performedAt: Long,
)

data class DayExerciseCount(val dayId: Long, val count: Int)
