package com.example.fitness_plan.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitness_plan.data.AppSettings
import com.example.fitness_plan.data.FitnessRepository
import com.example.fitness_plan.data.SettingsRepository
import com.example.fitness_plan.data.db.ExerciseEntity
import com.example.fitness_plan.data.db.PlanDayEntity
import com.example.fitness_plan.data.db.PlanDayExerciseDetail
import com.example.fitness_plan.data.db.PlanDayExerciseEntity
import com.example.fitness_plan.data.db.PlanEntity
import com.example.fitness_plan.data.model.MuscleGroup
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlanWithDays(val plan: PlanEntity, val days: List<PlanDayEntity>)

data class PlansUiState(
    val loading: Boolean = true,
    val plans: List<PlanWithDays> = emptyList(),
    val exerciseCounts: Map<Long, Int> = emptyMap(),
)

class PlansViewModel(private val repo: FitnessRepository) : ViewModel() {

    val state: StateFlow<PlansUiState> = combine(repo.plans, repo.allDays, repo.dayExerciseCounts) { plans, days, counts ->
        val byPlan = days.groupBy { it.planId }
        PlansUiState(
            loading = false,
            plans = plans.map { PlanWithDays(it, byPlan[it.id].orEmpty()) },
            exerciseCounts = counts.associate { it.dayId to it.count },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun createPlan(name: String) = viewModelScope.launch { repo.createPlan(name) }
    fun renamePlan(plan: PlanEntity, name: String) = viewModelScope.launch { repo.renamePlan(plan, name) }
    fun deletePlan(plan: PlanEntity) = viewModelScope.launch { repo.deletePlan(plan) }
    fun activate(plan: PlanEntity) = viewModelScope.launch {
        repo.activatePlan(plan.id)
        _messages.send("已启用「${plan.name}」")
    }
    fun reorderPlans(ids: List<Long>) = viewModelScope.launch { repo.reorderPlans(ids) }

    fun addDay(plan: PlanEntity, name: String) = viewModelScope.launch { repo.addDay(plan.id, name) }
    fun renameDay(day: PlanDayEntity, name: String) = viewModelScope.launch { repo.renameDay(day, name) }
    fun deleteDay(day: PlanDayEntity) = viewModelScope.launch { repo.deleteDay(day) }
    fun reorderDays(planId: Long, ids: List<Long>) = viewModelScope.launch { repo.reorderDays(planId, ids) }
    fun copyDay(day: PlanDayEntity, target: PlanEntity) = viewModelScope.launch {
        repo.copyDayToPlan(day, target.id)
        _messages.send("已复制到「${target.name}」")
    }
}

data class PlanDayUiState(
    val loading: Boolean = true,
    val day: PlanDayEntity? = null,
    val items: List<PlanDayExerciseDetail> = emptyList(),
    val library: List<ExerciseEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

class PlanDayViewModel(
    private val dayId: Long,
    private val repo: FitnessRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<PlanDayUiState> = combine(
        repo.observeDay(dayId), repo.observeDayExercises(dayId), repo.library, settings.settings,
    ) { day, items, library, s -> PlanDayUiState(false, day, items, library, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanDayUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun add(name: String, group: MuscleGroup) = viewModelScope.launch {
        val ok = repo.addExerciseToDay(
            dayId, name, group,
            FitnessRepository.DEFAULT_SETS, FitnessRepository.DEFAULT_REPS, FitnessRepository.DEFAULT_WEIGHT,
        )
        if (!ok) _messages.send("「$name」已经在这一天里了")
    }

    fun update(item: PlanDayExerciseEntity, weightKg: Double, sets: Int, reps: Int) = viewModelScope.launch {
        repo.updateDayExercise(item.copy(weightKg = weightKg, sets = sets, reps = reps))
    }

    fun remove(item: PlanDayExerciseEntity) = viewModelScope.launch { repo.removeDayExercise(item) }

    fun reorder(ids: List<Long>) = viewModelScope.launch { repo.reorderDayExercises(dayId, ids) }

    fun rename(name: String) = viewModelScope.launch { state.value.day?.let { repo.renameDay(it, name) } }
}
