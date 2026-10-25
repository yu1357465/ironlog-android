package com.example.fitness_plan.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitness_plan.data.AppSettings
import com.example.fitness_plan.data.FitnessRepository
import com.example.fitness_plan.data.SettingsRepository
import com.example.fitness_plan.data.WorkoutContext
import com.example.fitness_plan.data.db.ActiveExerciseDetail
import com.example.fitness_plan.data.db.EntryDetail
import com.example.fitness_plan.data.db.ExerciseEntity
import com.example.fitness_plan.data.db.PlanEntity
import com.example.fitness_plan.data.model.MuscleGroup
import com.example.fitness_plan.ui.UiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutUiState(
    val loading: Boolean = true,
    val context: WorkoutContext = WorkoutContext(null, emptyList()),
    val plans: List<PlanEntity> = emptyList(),
    val items: List<ActiveExerciseDetail> = emptyList(),
    val library: List<ExerciseEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
) {
    val completedCount: Int get() = items.count { it.item.completed }
    val remaining: Int get() = items.size - completedCount
}

class WorkoutViewModel(
    private val repo: FitnessRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<WorkoutUiState> = combine(
        repo.workoutContext, repo.plans, repo.activeExercises, repo.library, settingsRepo.settings,
    ) { ctx, plans, items, library, settings ->
        WorkoutUiState(false, ctx, plans, items, library, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private fun say(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _messages.trySend(UiMessage(text, actionLabel, action))
    }

    fun trend(exerciseId: Long): Flow<List<EntryDetail>> = repo.recentEntriesFor(exerciseId, 10)

    fun toggleCompleted(item: ActiveExerciseDetail) = viewModelScope.launch {
        repo.setActiveCompleted(item.item.id, !item.item.completed)
    }

    fun updateValues(item: ActiveExerciseDetail, weightKg: Double, sets: Int, reps: Int) = viewModelScope.launch {
        repo.updateActive(item.item.copy(weightKg = weightKg, sets = sets, reps = reps))
    }

    fun reorder(orderedIds: List<Long>) = viewModelScope.launch { repo.reorderActive(orderedIds) }

    fun setInPlan(item: ActiveExerciseDetail, inPlan: Boolean) = viewModelScope.launch {
        if (repo.setActiveInPlan(item.item.id, inPlan)) {
            say(if (inPlan) "已加入「${state.value.context.currentDay?.name}」模板" else "已从模板移除，本次仍保留")
        } else {
            say("当前没有启用的计划")
        }
    }

    fun replace(item: ActiveExerciseDetail, name: String, group: MuscleGroup) = viewModelScope.launch {
        repo.replaceActiveExercise(item.item.id, name, group)
    }

    fun delete(item: ActiveExerciseDetail, removeFromPlan: Boolean) = viewModelScope.launch {
        val deleted = repo.deleteActive(item.item.id, removeFromPlan) ?: return@launch
        say("已删除「${item.name}」", "撤销") { viewModelScope.launch { repo.restoreActive(deleted) } }
    }

    fun add(name: String, group: MuscleGroup, addToPlan: Boolean) = viewModelScope.launch {
        repo.addActiveExercise(name, group, addToPlan)
    }

    fun selectDay(dayId: Long) = viewModelScope.launch { repo.selectDay(dayId) }

    fun reloadDay() = viewModelScope.launch {
        repo.reloadCurrentDay()
        say("已按模板重新载入")
    }

    fun activatePlan(plan: PlanEntity) = viewModelScope.launch {
        repo.activatePlan(plan.id)
        say("已切换到「${plan.name}」")
    }

    fun finish(markAll: Boolean) = viewModelScope.launch {
        val ctx = state.value.context
        val days = ctx.days
        val next = days.getOrNull((days.indexOf(ctx.currentDay) + 1).mod(days.size.coerceAtLeast(1)))
        val count = repo.finishWorkout(markAll)
        when {
            count == 0 -> say("还没有完成任何动作")
            next != null && days.size > 1 -> say("已存档 $count 个动作，下次练：${next.name}")
            else -> say("已存档 $count 个动作")
        }
    }
}
