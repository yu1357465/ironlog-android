package io.github.yu1357465.ironlog.ui.plans

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yu1357465.ironlog.data.AppSettings
import io.github.yu1357465.ironlog.data.BackupManager
import io.github.yu1357465.ironlog.data.FitnessRepository
import io.github.yu1357465.ironlog.data.PlanShare
import io.github.yu1357465.ironlog.data.PlanShareException
import io.github.yu1357465.ironlog.data.SharedPlan
import io.github.yu1357465.ironlog.data.SettingsRepository
import io.github.yu1357465.ironlog.data.db.ExerciseEntity
import io.github.yu1357465.ironlog.data.db.PlanDayEntity
import io.github.yu1357465.ironlog.data.db.PlanDayExerciseDetail
import io.github.yu1357465.ironlog.data.db.PlanDayExerciseEntity
import io.github.yu1357465.ironlog.data.db.PlanEntity
import io.github.yu1357465.ironlog.data.model.MuscleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flowOn
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

class PlansViewModel(
    private val repo: FitnessRepository,
    private val backup: BackupManager,
) : ViewModel() {

    val state: StateFlow<PlansUiState> = combine(repo.plans, repo.allDays, repo.dayExerciseCounts) { plans, days, counts ->
        val byPlan = days.groupBy { it.planId }
        PlansUiState(
            loading = false,
            plans = plans.map { PlanWithDays(it, byPlan[it.id].orEmpty()) },
            exerciseCounts = counts.associate { it.dayId to it.count },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

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

    // ---------- 分享 ----------

    /** 正在分享的计划；非 null 时显示分享面板。 */
    var sharing by mutableStateOf<ShareState?>(null)
        private set

    fun openShare(plan: PlanEntity) = loadShare(plan.id, includeWeights = false)

    fun setIncludeWeights(value: Boolean) {
        sharing?.let { loadShare(it.planId, value) }
    }

    private fun loadShare(planId: Long, includeWeights: Boolean) = viewModelScope.launch {
        val shared = repo.exportPlan(planId, includeWeights) ?: return@launch
        sharing = ShareState(planId, includeWeights, shared, PlanShare.encode(shared))
    }

    fun closeShare() {
        sharing = null
    }

    fun exportFile() = viewModelScope.launch {
        val s = sharing ?: return@launch
        runCatching { backup.exportPlanFile(s.plan) }
            .onSuccess { _messages.send("已导出到 下载/${BackupManager.FOLDER}/${PlanShare.fileName(s.plan)}") }
            .onFailure { _messages.send("导出失败：${it.message}") }
    }

    /** 保存分享图到相册；[then] 收到保存后的 Uri（用于继续发送）。 */
    fun savePoster(poster: Bitmap, notify: Boolean, then: (Uri) -> Unit = {}) = viewModelScope.launch {
        val s = sharing ?: return@launch
        runCatching { backup.savePicture(poster, "计划_${s.plan.name}".take(40)) }
            .onSuccess { uri ->
                if (notify) _messages.send("已保存到相册（图片/${BackupManager.FOLDER}）")
                then(uri)
            }
            .onFailure { _messages.send("保存失败：${it.message}") }
    }

    /** 在后台生成分享图，成功后交给 [then]。 */
    fun launchPoster(build: suspend () -> Bitmap?, then: (Bitmap) -> Unit) = viewModelScope.launch {
        val bmp = build()
        if (bmp != null) then(bmp) else _messages.send("计划内容太多，无法生成二维码图片")
    }

    // ---------- 导入 ----------

    var importPreview by mutableStateOf<FitnessRepository.PlanImportPreview?>(null)
        private set

    /** 分享码文字、含分享码的聊天消息、扫码结果或计划文件内容，统一走这里。 */
    fun importFromText(text: String) = viewModelScope.launch {
        try {
            importPreview = repo.previewPlanImport(PlanShare.decode(text))
        } catch (e: PlanShareException) {
            _messages.send(e.message ?: "无法识别")
        }
    }

    fun importFromFile(uri: Uri) = viewModelScope.launch {
        runCatching { backup.readText(uri) }
            .onSuccess { importFromText(it) }
            .onFailure { _messages.send("读取失败：${it.message}") }
    }

    fun dismissImport() {
        importPreview = null
    }

    fun confirmImport() = viewModelScope.launch {
        val preview = importPreview ?: return@launch
        importPreview = null
        val plan = repo.importPlan(preview.plan)
        _messages.send(if (plan.isActive) "已导入并启用「${plan.name}」" else "已导入「${plan.name}」，点「启用」开始使用")
    }

    fun notify(text: String) = viewModelScope.launch { _messages.send(text) }
}

data class ShareState(
    val planId: Long,
    val includeWeights: Boolean,
    val plan: SharedPlan,
    val code: String,
)

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
