package com.example.fitness_plan.data

import androidx.room.withTransaction
import com.example.fitness_plan.data.db.ActiveExerciseDetail
import com.example.fitness_plan.data.db.ActiveExerciseEntity
import com.example.fitness_plan.data.db.AppDatabase
import com.example.fitness_plan.data.db.DayExerciseCount
import com.example.fitness_plan.data.db.EntryDetail
import com.example.fitness_plan.data.db.ExerciseEntity
import com.example.fitness_plan.data.db.PlanDayEntity
import com.example.fitness_plan.data.db.PlanDayExerciseDetail
import com.example.fitness_plan.data.db.PlanDayExerciseEntity
import com.example.fitness_plan.data.db.PlanEntity
import com.example.fitness_plan.data.db.WorkoutEntryEntity
import com.example.fitness_plan.data.db.WorkoutSessionEntity
import com.example.fitness_plan.data.model.MuscleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** 首页顶部显示的“当前计划 + 当前训练日”。 */
data class WorkoutContext(
    val plan: PlanEntity?,
    val days: List<PlanDayEntity>,
) {
    val currentDay: PlanDayEntity?
        get() = days.firstOrNull { it.id == plan?.currentDayId } ?: days.firstOrNull()
}

data class SessionWithEntries(
    val session: WorkoutSessionEntity,
    val entries: List<EntryDetail>,
) {
    val volumeKg: Double get() = entries.sumOf { it.entry.weightKg * it.entry.sets * it.entry.reps }
}

/** 删除卡片后用于“撤销”的快照。 */
data class DeletedActive(
    val item: ActiveExerciseEntity,
    val templateRow: PlanDayExerciseEntity?,
)

sealed interface NameResult {
    data class Ok(val id: Long) : NameResult
    data object Duplicate : NameResult
    data object Blank : NameResult
}

/**
 * 所有业务规则集中在这里，界面层只调用这些方法。
 * 涉及多表的操作都包在事务里，中途崩溃不会留下半截数据。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FitnessRepository(private val db: AppDatabase) {

    private val dao = db.dao()

    // ======================================================
    //  动作库
    // ======================================================

    val library: Flow<List<ExerciseEntity>> = dao.observeLibrary()

    /** 按名称取动作；不存在则新建，已归档则恢复。返回动作 id。 */
    private suspend fun obtainExercise(name: String, group: MuscleGroup): Long {
        val trimmed = name.trim()
        val existing = dao.findExerciseByName(trimmed)
        return when {
            existing == null -> dao.insertExercise(ExerciseEntity(name = trimmed, muscleGroup = group))
            existing.archived -> {
                dao.updateExercise(existing.copy(archived = false))
                existing.id
            }
            else -> existing.id
        }
    }

    suspend fun createExercise(name: String, group: MuscleGroup): NameResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return NameResult.Blank
        val existing = dao.findExerciseByName(trimmed)
        if (existing != null && !existing.archived) return NameResult.Duplicate
        return db.withTransaction {
            if (existing != null) {
                dao.updateExercise(existing.copy(archived = false, muscleGroup = group))
                NameResult.Ok(existing.id)
            } else {
                NameResult.Ok(dao.insertExercise(ExerciseEntity(name = trimmed, muscleGroup = group)))
            }
        }
    }

    suspend fun updateExercise(id: Long, name: String, group: MuscleGroup): NameResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return NameResult.Blank
        val current = dao.getExercise(id) ?: return NameResult.Blank
        val clash = dao.findExerciseByName(trimmed)
        if (clash != null && clash.id != id) return NameResult.Duplicate
        dao.updateExercise(current.copy(name = trimmed, muscleGroup = group))
        return NameResult.Ok(id)
    }

    suspend fun archiveExercise(id: Long) {
        dao.getExercise(id)?.let { dao.updateExercise(it.copy(archived = true)) }
    }

    suspend fun findExercise(name: String): ExerciseEntity? =
        dao.findExerciseByName(name.trim())?.takeUnless { it.archived }

    // ======================================================
    //  计划 / 训练日
    // ======================================================

    val plans: Flow<List<PlanEntity>> = dao.observePlans()
    val allDays: Flow<List<PlanDayEntity>> = dao.observeAllDays()
    val dayExerciseCounts: Flow<List<DayExerciseCount>> = dao.observeDayExerciseCounts()

    val workoutContext: Flow<WorkoutContext> = dao.observeActivePlan().flatMapLatest { plan ->
        if (plan == null) flowOf(WorkoutContext(null, emptyList()))
        else dao.observeDays(plan.id).map { WorkoutContext(plan, it) }
    }

    fun observeDay(dayId: Long): Flow<PlanDayEntity?> = dao.observeDay(dayId)
    fun observeDayExercises(dayId: Long): Flow<List<PlanDayExerciseDetail>> = dao.observeDayExercises(dayId)

    suspend fun createPlan(name: String): Long = db.withTransaction {
        val planId = dao.insertPlan(PlanEntity(name = name.trim(), sortOrder = dao.maxPlanOrder() + 1))
        val dayId = dao.insertDay(PlanDayEntity(planId = planId, name = "第 1 天"))
        dao.updatePlan(dao.getPlan(planId)!!.copy(currentDayId = dayId))
        // 还没有任何启用中的计划时，新计划直接启用
        if (dao.getActivePlan() == null) activatePlanInternal(planId)
        planId
    }

    suspend fun renamePlan(plan: PlanEntity, name: String) {
        dao.updatePlan(plan.copy(name = name.trim()))
    }

    suspend fun deletePlan(plan: PlanEntity) = db.withTransaction {
        dao.deletePlan(plan)
        if (plan.isActive) dao.clearActive()
    }

    suspend fun activatePlan(planId: Long) = db.withTransaction { activatePlanInternal(planId) }

    private suspend fun activatePlanInternal(planId: Long) {
        val plan = dao.getPlan(planId) ?: return
        dao.deactivateAllPlans()
        val days = dao.getDays(planId)
        val dayId = plan.currentDayId?.takeIf { id -> days.any { it.id == id } } ?: days.firstOrNull()?.id
        dao.updatePlan(plan.copy(isActive = true, currentDayId = dayId))
        loadDayIntoActive(dayId)
    }

    suspend fun reorderPlans(orderedIds: List<Long>) {
        val byId = dao.getPlans().associateBy { it.id }
        dao.updatePlans(orderedIds.mapIndexedNotNull { i, id -> byId[id]?.copy(sortOrder = i) })
    }

    suspend fun addDay(planId: Long, name: String): Long = db.withTransaction {
        val days = dao.getDays(planId)
        val id = dao.insertDay(PlanDayEntity(planId = planId, name = name.trim(), sortOrder = days.size))
        val plan = dao.getPlan(planId)
        if (plan != null && plan.currentDayId == null) {
            dao.updatePlan(plan.copy(currentDayId = id))
            if (plan.isActive) loadDayIntoActive(id)
        }
        id
    }

    suspend fun renameDay(day: PlanDayEntity, name: String) {
        dao.updateDay(day.copy(name = name.trim()))
    }

    suspend fun deleteDay(day: PlanDayEntity) = db.withTransaction {
        val days = dao.getDays(day.planId)
        dao.deleteDay(day)
        val plan = dao.getPlan(day.planId) ?: return@withTransaction
        if (plan.currentDayId == day.id) {
            val remaining = days.filter { it.id != day.id }
            val idx = days.indexOfFirst { it.id == day.id }
            val next = remaining.getOrNull(idx) ?: remaining.firstOrNull()
            dao.updatePlan(plan.copy(currentDayId = next?.id))
            if (plan.isActive) loadDayIntoActive(next?.id)
        }
    }

    suspend fun reorderDays(planId: Long, orderedIds: List<Long>) {
        val byId = dao.getDays(planId).associateBy { it.id }
        dao.updateDays(orderedIds.mapIndexedNotNull { i, id -> byId[id]?.copy(sortOrder = i) })
    }

    suspend fun copyDayToPlan(day: PlanDayEntity, targetPlanId: Long) = db.withTransaction {
        val targetDays = dao.getDays(targetPlanId)
        val name = if (targetDays.any { it.name == day.name }) "${day.name}（副本）" else day.name
        val newDayId = dao.insertDay(PlanDayEntity(planId = targetPlanId, name = name, sortOrder = targetDays.size))
        dao.insertDayExercises(dao.getDayExercises(day.id).map { it.copy(id = 0, dayId = newDayId) })
        val target = dao.getPlan(targetPlanId)
        if (target != null && target.currentDayId == null) {
            dao.updatePlan(target.copy(currentDayId = newDayId))
            if (target.isActive) loadDayIntoActive(newDayId)
        }
    }

    suspend fun addExerciseToDay(dayId: Long, name: String, group: MuscleGroup, sets: Int, reps: Int, weightKg: Double): Boolean =
        db.withTransaction {
            val exerciseId = obtainExercise(name, group)
            if (dao.findDayExercise(dayId, exerciseId) != null) return@withTransaction false
            dao.insertDayExercise(
                PlanDayExerciseEntity(
                    dayId = dayId, exerciseId = exerciseId, sets = sets, reps = reps,
                    weightKg = weightKg, sortOrder = dao.maxDayExerciseOrder(dayId) + 1,
                )
            )
            true
        }

    suspend fun updateDayExercise(item: PlanDayExerciseEntity) = dao.updateDayExercise(item)

    suspend fun removeDayExercise(item: PlanDayExerciseEntity) = dao.deleteDayExercise(item)

    suspend fun reorderDayExercises(dayId: Long, orderedIds: List<Long>) {
        val byId = dao.getDayExercises(dayId).associateBy { it.id }
        dao.updateDayExercises(orderedIds.mapIndexedNotNull { i, id -> byId[id]?.copy(sortOrder = i) })
    }

    // ======================================================
    //  今日训练
    // ======================================================

    val activeExercises: Flow<List<ActiveExerciseDetail>> = dao.observeActive()

    /** 清空训练台并按模板载入某一天；重量/组数/次数优先沿用该动作最近一次的实际记录。 */
    private suspend fun loadDayIntoActive(dayId: Long?) {
        dao.clearActive()
        if (dayId == null) return
        val items = dao.getDayExercises(dayId).mapIndexed { i, t ->
            val last = dao.latestEntryFor(t.exerciseId)
            ActiveExerciseEntity(
                exerciseId = t.exerciseId,
                weightKg = last?.weightKg ?: t.weightKg,
                sets = last?.sets ?: t.sets,
                reps = last?.reps ?: t.reps,
                inPlan = true,
                sortOrder = i,
            )
        }
        dao.insertActives(items)
    }

    private suspend fun currentDayId(): Long? = dao.getActivePlan()?.currentDayId

    suspend fun selectDay(dayId: Long) = db.withTransaction {
        val day = dao.getDay(dayId) ?: return@withTransaction
        val plan = dao.getPlan(day.planId) ?: return@withTransaction
        dao.updatePlan(plan.copy(currentDayId = dayId))
        if (plan.isActive) loadDayIntoActive(dayId)
    }

    /** 重新按模板载入当前训练日（丢弃今天的改动）。 */
    suspend fun reloadCurrentDay() = db.withTransaction { loadDayIntoActive(currentDayId()) }

    suspend fun addActiveExercise(name: String, group: MuscleGroup, addToPlan: Boolean) = db.withTransaction {
        val exerciseId = obtainExercise(name, group)
        val last = dao.latestEntryFor(exerciseId)
        val dayId = currentDayId()
        val template = dayId?.let { dao.findDayExercise(it, exerciseId) }
        val weight = last?.weightKg ?: template?.weightKg ?: DEFAULT_WEIGHT
        val sets = last?.sets ?: template?.sets ?: DEFAULT_SETS
        val reps = last?.reps ?: template?.reps ?: DEFAULT_REPS
        val inPlan = template != null || (addToPlan && dayId != null)
        if (dayId != null && addToPlan && template == null) {
            dao.insertDayExercise(
                PlanDayExerciseEntity(
                    dayId = dayId, exerciseId = exerciseId, sets = sets, reps = reps,
                    weightKg = weight, sortOrder = dao.maxDayExerciseOrder(dayId) + 1,
                )
            )
        }
        dao.insertActive(
            ActiveExerciseEntity(
                exerciseId = exerciseId, weightKg = weight, sets = sets, reps = reps,
                inPlan = inPlan, sortOrder = dao.maxActiveOrder() + 1,
            )
        )
    }

    suspend fun updateActive(item: ActiveExerciseEntity) = dao.updateActive(item)

    suspend fun setActiveCompleted(id: Long, completed: Boolean) {
        dao.getActiveById(id)?.let { dao.updateActive(it.copy(completed = completed)) }
    }

    /** 拖拽排序；属于模板的动作同步更新模板顺序，下次载入保持一致。 */
    suspend fun reorderActive(orderedIds: List<Long>) = db.withTransaction {
        val byId = dao.getActive().associateBy { it.id }
        val ordered = orderedIds.mapNotNull { byId[it] }
        dao.updateActives(ordered.mapIndexed { i, it -> it.copy(sortOrder = i) })
        val dayId = currentDayId() ?: return@withTransaction
        val templates = dao.getDayExercises(dayId).associateBy { it.exerciseId }
        val planOrder = ordered.filter { it.inPlan }.mapNotNull { templates[it.exerciseId] }
        val rest = templates.values.filter { t -> planOrder.none { it.id == t.id } }.sortedBy { it.sortOrder }
        dao.updateDayExercises((planOrder + rest).mapIndexed { i, t -> t.copy(sortOrder = i) })
    }

    suspend fun setActiveInPlan(id: Long, inPlan: Boolean): Boolean = db.withTransaction {
        val item = dao.getActiveById(id) ?: return@withTransaction false
        val dayId = currentDayId() ?: return@withTransaction false
        if (inPlan) {
            if (dao.findDayExercise(dayId, item.exerciseId) == null) {
                dao.insertDayExercise(
                    PlanDayExerciseEntity(
                        dayId = dayId, exerciseId = item.exerciseId, sets = item.sets, reps = item.reps,
                        weightKg = item.weightKg, sortOrder = dao.maxDayExerciseOrder(dayId) + 1,
                    )
                )
            }
        } else {
            dao.deleteDayExerciseByExercise(dayId, item.exerciseId)
        }
        dao.updateActive(item.copy(inPlan = inPlan))
        true
    }

    /** 把卡片换成另一个动作；若属于模板，模板里也一并替换。 */
    suspend fun replaceActiveExercise(id: Long, name: String, group: MuscleGroup) = db.withTransaction {
        val item = dao.getActiveById(id) ?: return@withTransaction
        val newId = obtainExercise(name, group)
        if (newId == item.exerciseId) return@withTransaction
        val dayId = currentDayId()
        if (item.inPlan && dayId != null) {
            val oldRow = dao.findDayExercise(dayId, item.exerciseId)
            val clash = dao.findDayExercise(dayId, newId)
            when {
                oldRow != null && clash == null -> dao.updateDayExercise(oldRow.copy(exerciseId = newId))
                oldRow != null -> dao.deleteDayExercise(oldRow)
            }
        }
        val last = dao.latestEntryFor(newId)
        dao.updateActive(
            item.copy(
                exerciseId = newId,
                weightKg = last?.weightKg ?: item.weightKg,
                sets = last?.sets ?: item.sets,
                reps = last?.reps ?: item.reps,
            )
        )
    }

    suspend fun deleteActive(id: Long, removeFromPlan: Boolean): DeletedActive? = db.withTransaction {
        val item = dao.getActiveById(id) ?: return@withTransaction null
        var templateRow: PlanDayExerciseEntity? = null
        if (removeFromPlan) {
            currentDayId()?.let { dayId ->
                templateRow = dao.findDayExercise(dayId, item.exerciseId)
                templateRow?.let { dao.deleteDayExercise(it) }
            }
        }
        dao.deleteActive(item)
        DeletedActive(item, templateRow)
    }

    suspend fun restoreActive(deleted: DeletedActive) = db.withTransaction {
        dao.insertActive(deleted.item)
        deleted.templateRow?.let { row ->
            if (dao.findDayExercise(row.dayId, row.exerciseId) == null && dao.getDay(row.dayId) != null) {
                dao.insertDayExercise(row)
            }
        }
    }

    /**
     * 结束训练：把已完成（或全部）动作存入历史，清空训练台，并自动切换到下一个训练日。
     * @return 存档的动作数；为 0 时不做任何改动。
     */
    suspend fun finishWorkout(markAllCompleted: Boolean, now: Long = System.currentTimeMillis()): Int =
        db.withTransaction {
            val active = dao.getActive()
            val done = if (markAllCompleted) active else active.filter { it.completed }
            if (done.isEmpty()) return@withTransaction 0

            val plan = dao.getActivePlan()
            val days = plan?.let { dao.getDays(it.id) }.orEmpty()
            val currentDay = days.firstOrNull { it.id == plan?.currentDayId }
            val sessionId = dao.insertSession(
                WorkoutSessionEntity(performedAt = now, planName = plan?.name, dayName = currentDay?.name)
            )
            dao.insertEntries(done.mapIndexed { i, a ->
                WorkoutEntryEntity(
                    sessionId = sessionId, exerciseId = a.exerciseId,
                    weightKg = a.weightKg, sets = a.sets, reps = a.reps, sortOrder = i,
                )
            })

            if (plan != null && days.isNotEmpty()) {
                val idx = days.indexOfFirst { it.id == currentDay?.id }
                val next = days[(idx + 1).mod(days.size)]
                dao.updatePlan(plan.copy(currentDayId = next.id))
                loadDayIntoActive(next.id)
            } else {
                dao.clearActive()
            }
            done.size
        }

    fun recentEntriesFor(exerciseId: Long, limit: Int = 10): Flow<List<EntryDetail>> =
        dao.observeRecentEntriesFor(exerciseId, limit)

    // ======================================================
    //  历史
    // ======================================================

    val allEntries: Flow<List<EntryDetail>> = dao.observeAllEntries()

    val sessions: Flow<List<SessionWithEntries>> =
        combine(dao.observeSessions(), dao.observeAllEntries()) { sessions, entries ->
            val bySession = entries.groupBy { it.entry.sessionId }
            sessions.map { SessionWithEntries(it, bySession[it.id].orEmpty()) }
        }.flowOn(Dispatchers.Default)

    suspend fun updateEntry(entry: WorkoutEntryEntity) = dao.updateEntry(entry)

    suspend fun deleteEntry(id: Long) = db.withTransaction {
        dao.deleteEntry(id)
        dao.deleteEmptySessions()
    }

    suspend fun updateSession(session: WorkoutSessionEntity) = dao.updateSession(session)

    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    suspend fun addManualEntry(
        performedAt: Long, dayName: String, exerciseName: String, group: MuscleGroup,
        weightKg: Double, sets: Int, reps: Int,
    ) = db.withTransaction {
        val exerciseId = obtainExercise(exerciseName, group)
        val sessionId = dao.insertSession(
            WorkoutSessionEntity(performedAt = performedAt, planName = "手动补录", dayName = dayName.ifBlank { null })
        )
        dao.insertEntries(listOf(WorkoutEntryEntity(sessionId = sessionId, exerciseId = exerciseId, weightKg = weightKg, sets = sets, reps = reps)))
    }

    suspend fun clearHistory() = dao.clearHistory()

    // ======================================================
    //  首次启动的默认数据
    // ======================================================

    suspend fun seedIfEmpty() = db.withTransaction {
        if (dao.countExercises() > 0 || dao.getPlans().isNotEmpty()) return@withTransaction
        val ids = SeedData.exercises.associate { (name, group) ->
            name to dao.insertExercise(ExerciseEntity(name = name, muscleGroup = group))
        }
        val planId = dao.insertPlan(PlanEntity(name = SeedData.PLAN_NAME))
        SeedData.days.forEachIndexed { dayIndex, (dayName, items) ->
            val dayId = dao.insertDay(PlanDayEntity(planId = planId, name = dayName, sortOrder = dayIndex))
            dao.insertDayExercises(items.mapIndexed { i, t ->
                PlanDayExerciseEntity(
                    dayId = dayId, exerciseId = ids.getValue(t.name), sets = t.sets,
                    reps = t.reps, weightKg = t.weightKg, sortOrder = i,
                )
            })
        }
        activatePlanInternal(planId)
    }

    companion object {
        const val DEFAULT_WEIGHT = 20.0
        const val DEFAULT_SETS = 4
        const val DEFAULT_REPS = 12
    }
}
