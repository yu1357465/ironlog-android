package io.github.yu1357465.ironlog.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yu1357465.ironlog.data.db.PlanDayExerciseDetail
import io.github.yu1357465.ironlog.ui.appViewModel
import io.github.yu1357465.ironlog.ui.components.EmptyState
import io.github.yu1357465.ironlog.ui.components.ExercisePickerSheet
import io.github.yu1357465.ironlog.ui.components.FlatCard
import io.github.yu1357465.ironlog.ui.components.LocalSnackbar
import io.github.yu1357465.ironlog.ui.components.MuscleBadge
import io.github.yu1357465.ironlog.ui.components.ScreenHeader
import io.github.yu1357465.ironlog.ui.components.SetEditorSheet
import io.github.yu1357465.ironlog.ui.components.TextInputDialog
import io.github.yu1357465.ironlog.ui.components.formatWeight
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** 编辑某个训练日的模板动作。 */
@Composable
fun PlanDayScreen(dayId: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "day$dayId") { PlanDayViewModel(dayId, it.repository, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val haptic = LocalHapticFeedback.current
    val unit = state.settings.unit

    var showAdd by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlanDayExerciseDetail?>(null) }
    var list by remember { mutableStateOf(state.items) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.items) { if (!dragging) list = state.items }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(state.loading, state.day) { if (!state.loading && state.day == null) onBack() }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val a = list.indexOfFirst { it.item.id == from.key }
        val b = list.indexOfFirst { it.item.id == to.key }
        if (a >= 0 && b >= 0) {
            list = list.toMutableList().apply { add(b, removeAt(a)) }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = state.day?.name.orEmpty(),
                subtitle = "训练日模板 · ${list.size} 个动作",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showRename = true }) { Icon(Icons.Outlined.DriveFileRenameOutline, "重命名") }
                },
            )
            if (!state.loading && list.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.FitnessCenter,
                    title = "还没有动作",
                    message = "这里设定的组数、次数、重量是默认值；做过一次之后，会自动沿用你上次的实际数据",
                )
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.item.id }) { item ->
                    ReorderableItem(reorderState, key = item.item.id) {
                        FlatCard(Modifier.fillMaxWidth(), onClick = { editing = item }) {
                            Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        MuscleBadge(item.muscleGroup)
                                        Text(
                                            "  ${item.item.sets} 组 × ${item.item.reps} 次 · ${formatWeight(item.item.weightKg, unit)} ${unit.label}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { vm.remove(item.item) }) {
                                    Icon(Icons.Outlined.Close, "移除", tint = MaterialTheme.colorScheme.outline)
                                }
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = { dragging = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        onDragStopped = { dragging = false; vm.reorder(list.map { it.item.id }) },
                                    ),
                                ) { Icon(Icons.Default.DragHandle, "拖动排序", tint = MaterialTheme.colorScheme.outline) }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("添加动作") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        )
    }

    if (showAdd) {
        ExercisePickerSheet(
            title = "添加到「${state.day?.name}」",
            library = state.library,
            confirmLabel = "添加",
            onConfirm = { name, group, _ -> vm.add(name, group) },
            onDismiss = { showAdd = false },
        )
    }
    if (showRename) {
        TextInputDialog("重命名训练日", "名称", state.day?.name.orEmpty(), onConfirm = vm::rename, onDismiss = { showRename = false })
    }
    editing?.let { item ->
        SetEditorSheet(
            title = "${item.name}（默认值）",
            unit = unit,
            weightKg = item.item.weightKg,
            sets = item.item.sets,
            reps = item.item.reps,
            onConfirm = { w, s, r -> vm.update(item.item, w, s, r) },
            onDismiss = { editing = null },
        )
    }
}
