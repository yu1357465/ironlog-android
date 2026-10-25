package com.example.fitness_plan.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FitnessDao {

    // ---------- 动作库 ----------

    @Query("SELECT * FROM exercises WHERE archived = 0 ORDER BY name")
    fun observeLibrary(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun findExerciseByName(name: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExercise(id: Long): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun countExercises(): Int

    @Insert
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    // ---------- 计划 ----------

    @Query("SELECT * FROM plans ORDER BY sortOrder, id")
    fun observePlans(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans ORDER BY sortOrder, id")
    suspend fun getPlans(): List<PlanEntity>

    @Query("SELECT * FROM plans WHERE isActive = 1 LIMIT 1")
    fun observeActivePlan(): Flow<PlanEntity?>

    @Query("SELECT * FROM plans WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePlan(): PlanEntity?

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun getPlan(id: Long): PlanEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM plans")
    suspend fun maxPlanOrder(): Int

    @Insert
    suspend fun insertPlan(plan: PlanEntity): Long

    @Update
    suspend fun updatePlan(plan: PlanEntity)

    @Update
    suspend fun updatePlans(plans: List<PlanEntity>)

    @Delete
    suspend fun deletePlan(plan: PlanEntity)

    @Query("UPDATE plans SET isActive = 0")
    suspend fun deactivateAllPlans()

    // ---------- 训练日 ----------

    @Query("SELECT * FROM plan_days ORDER BY planId, sortOrder, id")
    fun observeAllDays(): Flow<List<PlanDayEntity>>

    @Query("SELECT * FROM plan_days WHERE planId = :planId ORDER BY sortOrder, id")
    fun observeDays(planId: Long): Flow<List<PlanDayEntity>>

    @Query("SELECT * FROM plan_days WHERE planId = :planId ORDER BY sortOrder, id")
    suspend fun getDays(planId: Long): List<PlanDayEntity>

    @Query("SELECT * FROM plan_days WHERE id = :id")
    suspend fun getDay(id: Long): PlanDayEntity?

    @Query("SELECT * FROM plan_days WHERE id = :id")
    fun observeDay(id: Long): Flow<PlanDayEntity?>

    @Insert
    suspend fun insertDay(day: PlanDayEntity): Long

    @Update
    suspend fun updateDay(day: PlanDayEntity)

    @Update
    suspend fun updateDays(days: List<PlanDayEntity>)

    @Delete
    suspend fun deleteDay(day: PlanDayEntity)

    @Query("SELECT dayId, COUNT(*) AS count FROM plan_day_exercises GROUP BY dayId")
    fun observeDayExerciseCounts(): Flow<List<DayExerciseCount>>

    // ---------- 训练日模板动作 ----------

    @Query(
        "SELECT d.*, e.name AS exerciseName, e.muscleGroup AS exerciseGroup " +
            "FROM plan_day_exercises d INNER JOIN exercises e ON e.id = d.exerciseId " +
            "WHERE d.dayId = :dayId ORDER BY d.sortOrder, d.id"
    )
    fun observeDayExercises(dayId: Long): Flow<List<PlanDayExerciseDetail>>

    @Query("SELECT * FROM plan_day_exercises WHERE dayId = :dayId ORDER BY sortOrder, id")
    suspend fun getDayExercises(dayId: Long): List<PlanDayExerciseEntity>

    @Query("SELECT * FROM plan_day_exercises WHERE dayId = :dayId AND exerciseId = :exerciseId LIMIT 1")
    suspend fun findDayExercise(dayId: Long, exerciseId: Long): PlanDayExerciseEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM plan_day_exercises WHERE dayId = :dayId")
    suspend fun maxDayExerciseOrder(dayId: Long): Int

    @Insert
    suspend fun insertDayExercise(item: PlanDayExerciseEntity): Long

    @Insert
    suspend fun insertDayExercises(items: List<PlanDayExerciseEntity>)

    @Update
    suspend fun updateDayExercise(item: PlanDayExerciseEntity)

    @Update
    suspend fun updateDayExercises(items: List<PlanDayExerciseEntity>)

    @Delete
    suspend fun deleteDayExercise(item: PlanDayExerciseEntity)

    @Query("DELETE FROM plan_day_exercises WHERE dayId = :dayId AND exerciseId = :exerciseId")
    suspend fun deleteDayExerciseByExercise(dayId: Long, exerciseId: Long)

    // ---------- 今日训练台 ----------

    @Query(
        "SELECT a.*, e.name AS exerciseName, e.muscleGroup AS exerciseGroup " +
            "FROM active_exercises a INNER JOIN exercises e ON e.id = a.exerciseId " +
            "ORDER BY a.sortOrder, a.id"
    )
    fun observeActive(): Flow<List<ActiveExerciseDetail>>

    @Query("SELECT * FROM active_exercises ORDER BY sortOrder, id")
    suspend fun getActive(): List<ActiveExerciseEntity>

    @Query("SELECT * FROM active_exercises WHERE id = :id")
    suspend fun getActiveById(id: Long): ActiveExerciseEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM active_exercises")
    suspend fun maxActiveOrder(): Int

    @Insert
    suspend fun insertActive(item: ActiveExerciseEntity): Long

    @Insert
    suspend fun insertActives(items: List<ActiveExerciseEntity>)

    @Update
    suspend fun updateActive(item: ActiveExerciseEntity)

    @Update
    suspend fun updateActives(items: List<ActiveExerciseEntity>)

    @Delete
    suspend fun deleteActive(item: ActiveExerciseEntity)

    @Query("DELETE FROM active_exercises")
    suspend fun clearActive()

    // ---------- 历史 ----------

    @Query("SELECT * FROM workout_sessions ORDER BY performedAt DESC, id DESC")
    fun observeSessions(): Flow<List<WorkoutSessionEntity>>

    @Query(
        "SELECT w.*, e.name AS exerciseName, e.muscleGroup AS exerciseGroup, s.performedAt AS performedAt " +
            "FROM workout_entries w " +
            "INNER JOIN exercises e ON e.id = w.exerciseId " +
            "INNER JOIN workout_sessions s ON s.id = w.sessionId " +
            "ORDER BY s.performedAt DESC, w.sortOrder, w.id"
    )
    fun observeAllEntries(): Flow<List<EntryDetail>>

    @Query(
        "SELECT w.* FROM workout_entries w INNER JOIN workout_sessions s ON s.id = w.sessionId " +
            "WHERE w.exerciseId = :exerciseId ORDER BY s.performedAt DESC, w.id DESC LIMIT 1"
    )
    suspend fun latestEntryFor(exerciseId: Long): WorkoutEntryEntity?

    @Query(
        "SELECT w.*, e.name AS exerciseName, e.muscleGroup AS exerciseGroup, s.performedAt AS performedAt " +
            "FROM workout_entries w " +
            "INNER JOIN exercises e ON e.id = w.exerciseId " +
            "INNER JOIN workout_sessions s ON s.id = w.sessionId " +
            "WHERE w.exerciseId = :exerciseId ORDER BY s.performedAt DESC, w.id DESC LIMIT :limit"
    )
    fun observeRecentEntriesFor(exerciseId: Long, limit: Int): Flow<List<EntryDetail>>

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Insert
    suspend fun insertEntries(entries: List<WorkoutEntryEntity>)

    @Update
    suspend fun updateEntry(entry: WorkoutEntryEntity)

    @Query("DELETE FROM workout_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM workout_sessions WHERE id NOT IN (SELECT DISTINCT sessionId FROM workout_entries)")
    suspend fun deleteEmptySessions()

    @Query("DELETE FROM workout_sessions")
    suspend fun clearHistory()

    // ---------- 备份/恢复 ----------

    @Query("SELECT * FROM exercises") suspend fun allExercises(): List<ExerciseEntity>
    @Query("SELECT * FROM plans") suspend fun allPlans(): List<PlanEntity>
    @Query("SELECT * FROM plan_days") suspend fun allDays(): List<PlanDayEntity>
    @Query("SELECT * FROM plan_day_exercises") suspend fun allDayExercises(): List<PlanDayExerciseEntity>
    @Query("SELECT * FROM active_exercises") suspend fun allActive(): List<ActiveExerciseEntity>
    @Query("SELECT * FROM workout_sessions ORDER BY performedAt") suspend fun allSessions(): List<WorkoutSessionEntity>
    @Query("SELECT * FROM workout_entries") suspend fun allEntries(): List<WorkoutEntryEntity>

    @Query(
        "SELECT w.*, e.name AS exerciseName, e.muscleGroup AS exerciseGroup, s.performedAt AS performedAt " +
            "FROM workout_entries w " +
            "INNER JOIN exercises e ON e.id = w.exerciseId " +
            "INNER JOIN workout_sessions s ON s.id = w.sessionId " +
            "ORDER BY s.performedAt, w.sortOrder, w.id"
    )
    suspend fun allEntryDetails(): List<EntryDetail>

    @Insert suspend fun insertExercises(items: List<ExerciseEntity>)
    @Insert suspend fun insertPlans(items: List<PlanEntity>)
    @Insert suspend fun insertDays(items: List<PlanDayEntity>)
    @Insert suspend fun insertSessions(items: List<WorkoutSessionEntity>)
}
