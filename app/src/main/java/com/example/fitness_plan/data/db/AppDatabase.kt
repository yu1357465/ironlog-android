package com.example.fitness_plan.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.fitness_plan.data.model.MuscleGroup

class Converters {
    @TypeConverter
    fun muscleGroupToString(group: MuscleGroup): String = group.name

    @TypeConverter
    fun stringToMuscleGroup(value: String?): MuscleGroup = MuscleGroup.fromName(value)
}

/**
 * 以后改表结构时：version + 1，并在 [build] 里 addMigrations(...)。
 * 不使用 fallbackToDestructiveMigration，避免升级失败时静默清空训练记录。
 */
@Database(
    entities = [
        ExerciseEntity::class,
        PlanEntity::class,
        PlanDayEntity::class,
        PlanDayExerciseEntity::class,
        ActiveExerciseEntity::class,
        WorkoutSessionEntity::class,
        WorkoutEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FitnessDao

    companion object {
        // 新文件名：与旧版 Java 实现的 fitness_plan_database 完全分开
        private const val NAME = "fitness.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME).build()
    }
}
