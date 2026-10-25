package com.example.fitness_plan.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.fitness_plan.data.FitnessRepository
import com.example.fitness_plan.data.NameResult
import com.example.fitness_plan.data.db.ExerciseEntity
import com.example.fitness_plan.data.model.MuscleGroup
import com.example.fitness_plan.ui.appViewModel
import com.example.fitness_plan.ui.components.ConfirmDialog
import com.example.fitness_plan.ui.components.EmptyState
import com.example.fitness_plan.ui.components.FlatCard
import com.example.fitness_plan.ui.components.LocalSnackbar
import com.example.fitness_plan.ui.components.MuscleBadge
import com.example.fitness_plan.ui.components.ScreenHeader
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val repo: FitnessRepository) : ViewModel() {
    val library = repo.library.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun save(existing: ExerciseEntity?, name: String, group: MuscleGroup, onDone: () -> Unit) = viewModelScope.launch {
        val result = if (existing == null) repo.createExercise(name, group) else repo.updateExercise(existing.id, name, group)
        when (result) {
            is NameResult.Ok -> onDone()
            NameResult.Duplicate -> _messages.send("「${name.trim()}」已经存在")
            NameResult.Blank -> Unit
        }
    }

    fun archive(exercise: ExerciseEntity) = viewModelScope.launch {
        repo.archiveExercise(exercise.id)
        _messages.send("已从动作库移除，历史记录保留")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(onBack: () -> Unit) {
    val vm = appViewModel { LibraryViewModel(it.repository) }
    val library by vm.library.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var editing by remember { mutableStateOf<ExerciseEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val all = library.orEmpty()
    val shown = all.filter {
        (filter == null || it.muscleGroup == filter) && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("动作库", subtitle = "${all.size} 个动作", onBack = onBack)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索动作") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "清除") } },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("全部") }) }
                items(MuscleGroup.entries) { g ->
                    FilterChip(selected = filter == g, onClick = { filter = if (filter == g) null else g }, label = { Text(g.label) })
                }
            }

            if (library != null && shown.isEmpty()) {
                EmptyState(Icons.Outlined.SearchOff, "没有找到动作", if (query.isNotBlank()) "试试换个关键词，或直接新建" else "点右下角新建一个动作")
            }
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp)) {
                item {
                    if (shown.isNotEmpty()) {
                        FlatCard(Modifier.fillMaxWidth()) {
                            shown.forEachIndexed { i, ex ->
                                if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    Modifier.fillMaxWidth().clickable { editing = ex }.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(ex.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    MuscleBadge(ex.muscleGroup)
                                }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("新建动作") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        )
    }

    if (creating || editing != null) {
        ExerciseEditDialog(
            exercise = editing,
            initialName = if (creating) query.trim() else "",
            onSave = { name, group -> vm.save(editing, name, group) { creating = false; editing = null } },
            onArchive = editing?.let { ex -> { vm.archive(ex); editing = null } },
            onDismiss = { creating = false; editing = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseEditDialog(
    exercise: ExerciseEntity?,
    initialName: String,
    onSave: (String, MuscleGroup) -> Unit,
    onArchive: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(exercise?.name ?: initialName) }
    var group by remember { mutableStateOf(exercise?.muscleGroup ?: MuscleGroup.CHEST) }
    var confirmArchive by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exercise == null) "新建动作" else "编辑动作") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Text("目标肌群（用于力量雷达图）", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuscleGroup.entries.forEach { g ->
                        FilterChip(selected = g == group, onClick = { group = g }, label = { Text(g.label) })
                    }
                }
                if (exercise != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "改名或改分类会同步到所有计划和历史记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, group) }) { Text("保存") } },
        dismissButton = {
            Row {
                if (onArchive != null) {
                    TextButton(onClick = { confirmArchive = true }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
    if (confirmArchive && onArchive != null) {
        ConfirmDialog(
            "删除「${exercise?.name}」？",
            "它会从动作库和联想里消失，但已有的历史记录和计划仍会保留并正常显示。",
            "删除", onConfirm = onArchive, onDismiss = { confirmArchive = false }, destructive = true,
        )
    }
}
