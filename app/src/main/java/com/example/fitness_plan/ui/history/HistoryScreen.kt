package com.example.fitness_plan.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.fitness_plan.data.AppSettings
import com.example.fitness_plan.data.FitnessRepository
import com.example.fitness_plan.data.SessionWithEntries
import com.example.fitness_plan.data.SettingsRepository
import com.example.fitness_plan.data.db.EntryDetail
import com.example.fitness_plan.data.db.ExerciseEntity
import com.example.fitness_plan.data.db.WorkoutSessionEntity
import com.example.fitness_plan.data.model.MuscleGroup
import com.example.fitness_plan.data.model.WeightUnit
import com.example.fitness_plan.ui.appViewModel
import com.example.fitness_plan.ui.components.ConfirmDialog
import com.example.fitness_plan.ui.components.EmptyState
import com.example.fitness_plan.ui.components.ExercisePickerSheet
import com.example.fitness_plan.ui.components.FlatCard
import com.example.fitness_plan.ui.components.MuscleDot
import com.example.fitness_plan.ui.components.ScreenHeader
import com.example.fitness_plan.ui.components.SetEditorSheet
import com.example.fitness_plan.ui.components.TextInputDialog
import com.example.fitness_plan.ui.components.formatFullDate
import com.example.fitness_plan.ui.components.formatSessionDate
import com.example.fitness_plan.ui.components.formatVolume
import com.example.fitness_plan.ui.components.formatWeight
import com.example.fitness_plan.ui.components.relativeDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

data class HistoryUiState(
    val loading: Boolean = true,
    val sessions: List<SessionWithEntries> = emptyList(),
    val library: List<ExerciseEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

class HistoryViewModel(private val repo: FitnessRepository, settings: SettingsRepository) : ViewModel() {
    val state = combine(repo.sessions, repo.library, settings.settings) { s, l, st -> HistoryUiState(false, s, l, st) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun updateEntry(entry: EntryDetail, weightKg: Double, sets: Int, reps: Int) = viewModelScope.launch {
        repo.updateEntry(entry.entry.copy(weightKg = weightKg, sets = sets, reps = reps))
    }
    fun deleteEntry(entry: EntryDetail) = viewModelScope.launch { repo.deleteEntry(entry.entry.id) }
    fun renameSession(session: WorkoutSessionEntity, dayName: String) = viewModelScope.launch {
        repo.updateSession(session.copy(dayName = dayName))
    }
    fun deleteSession(session: WorkoutSessionEntity) = viewModelScope.launch { repo.deleteSession(session.id) }
    fun addManual(date: Long, dayName: String, name: String, group: MuscleGroup, weightKg: Double, sets: Int, reps: Int) =
        viewModelScope.launch { repo.addManualEntry(date, dayName, name, group, weightKg, sets, reps) }
}

@Composable
fun HistoryScreen() {
    val vm = appViewModel { HistoryViewModel(it.repository, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val unit = state.settings.unit

    var expanded by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    if (!initialized && state.sessions.isNotEmpty()) {
        expanded = setOf(state.sessions.first().session.id)
        initialized = true
    }
    var editingEntry by remember { mutableStateOf<EntryDetail?>(null) }
    var renaming by remember { mutableStateOf<WorkoutSessionEntity?>(null) }
    var deleting by remember { mutableStateOf<WorkoutSessionEntity?>(null) }
    var manualExercise by remember { mutableStateOf<Pair<String, MuscleGroup>?>(null) }
    var pickingManual by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        val last = state.sessions.firstOrNull()?.session?.performedAt
        ScreenHeader(
            "历史",
            subtitle = if (state.sessions.isEmpty()) null else "共 ${state.sessions.size} 次训练 · 上次 ${relativeDay(last!!)}",
            actions = { IconButton(onClick = { pickingManual = true }) { Icon(Icons.Outlined.PostAdd, "补录训练") } },
        )
        if (!state.loading && state.sessions.isEmpty()) {
            EmptyState(
                Icons.Outlined.History, "还没有训练记录",
                "在训练页完成训练后会自动存档到这里；之前练过的也可以手动补录",
                action = { OutlinedButton(onClick = { pickingManual = true }) { Text("补录一条") } },
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.sessions, key = { it.session.id }) { s ->
                SessionCard(
                    s = s,
                    unit = unit,
                    expanded = s.session.id in expanded,
                    onToggle = { expanded = if (s.session.id in expanded) expanded - s.session.id else expanded + s.session.id },
                    onEditEntry = { editingEntry = it },
                    onRename = { renaming = s.session },
                    onDelete = { deleting = s.session },
                )
            }
        }
    }

    editingEntry?.let { e ->
        SetEditorSheet(
            title = e.name,
            unit = unit,
            weightKg = e.entry.weightKg,
            sets = e.entry.sets,
            reps = e.entry.reps,
            onConfirm = { w, s, r -> vm.updateEntry(e, w, s, r) },
            onDismiss = { editingEntry = null },
            header = {
                Text(formatFullDate(e.performedAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            },
            footer = {
                TextButton(onClick = { vm.deleteEntry(e); editingEntry = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("删除这条记录", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
    renaming?.let { s ->
        TextInputDialog("修改训练日名称", "训练日", s.dayName.orEmpty(), onConfirm = { vm.renameSession(s, it) }, onDismiss = { renaming = null })
    }
    deleting?.let { s ->
        ConfirmDialog(
            "删除这次训练？", "${formatFullDate(s.performedAt)} 的全部记录将被删除，无法恢复。", "删除",
            onConfirm = { vm.deleteSession(s) }, onDismiss = { deleting = null }, destructive = true,
        )
    }
    if (pickingManual) {
        ExercisePickerSheet(
            title = "补录：选择动作",
            library = state.library,
            confirmLabel = "下一步",
            onConfirm = { name, group, _ -> manualExercise = name to group },
            onDismiss = { pickingManual = false },
        )
    }
    manualExercise?.let { (name, group) ->
        ManualEntrySheet(name, unit, onConfirm = { date, day, w, s, r -> vm.addManual(date, day, name, group, w, s, r) }, onDismiss = { manualExercise = null })
    }
}

@Composable
private fun SessionCard(
    s: SessionWithEntries,
    unit: WeightUnit,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEditEntry: (EntryDetail) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")
    FlatCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(formatSessionDate(s.session.performedAt), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(s.session.dayName, "${s.entries.size} 个动作", "${formatVolume(s.volumeKg, unit)} ${unit.label}").joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    s.entries.map { it.muscleGroup }.distinct().forEach { MuscleDot(it) }
                }
            }
            Icon(
                Icons.Default.KeyboardArrowDown, null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.rotate(arrow),
            )
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多", tint = MaterialTheme.colorScheme.outline) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("修改训练日名称") }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(
                        text = { Text("删除整次训练", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
        AnimatedVisibility(expanded) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                s.entries.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onEditEntry(e) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MuscleDot(e.muscleGroup)
                        Spacer(Modifier.width(10.dp))
                        Text(e.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatWeight(e.entry.weightKg, unit)} ${unit.label}  ·  ${e.entry.sets}×${e.entry.reps}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                s.session.planName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualEntrySheet(
    name: String,
    unit: WeightUnit,
    onConfirm: (date: Long, dayName: String, weightKg: Double, sets: Int, reps: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var dayName by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }

    SetEditorSheet(
        title = "补录：$name",
        unit = unit,
        weightKg = FitnessRepository.DEFAULT_WEIGHT,
        sets = FitnessRepository.DEFAULT_SETS,
        reps = FitnessRepository.DEFAULT_REPS,
        confirmLabel = "保存记录",
        onConfirm = { w, s, r -> onConfirm(date, dayName.trim(), w, s, r) },
        onDismiss = onDismiss,
        header = {
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CalendarMonth, null)
                Spacer(Modifier.width(8.dp))
                Text(formatFullDate(date))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(dayName, { dayName = it }, label = { Text("训练日名称（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        },
    )

    if (pickDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = toUtcMidnight(date),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = fromUtcMidnight(it) }
                    pickDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("取消") } },
        ) { DatePicker(pickerState) }
    }
}

/** DatePicker 使用 UTC 零点；这里与本地时区的中午互转，避免跨时区日期偏移。 */
private fun toUtcMidnight(local: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = local }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun fromUtcMidnight(utc: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return Calendar.getInstance().apply {
        clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 12, 0)
    }.timeInMillis
}
