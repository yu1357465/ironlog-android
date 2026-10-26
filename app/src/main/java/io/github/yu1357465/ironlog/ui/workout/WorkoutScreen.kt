package io.github.yu1357465.ironlog.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yu1357465.ironlog.ui.theme.AppIcons
import io.github.yu1357465.ironlog.data.db.ActiveExerciseDetail
import io.github.yu1357465.ironlog.data.model.WeightUnit
import io.github.yu1357465.ironlog.ui.appViewModel
import io.github.yu1357465.ironlog.ui.components.ChartPoint
import io.github.yu1357465.ironlog.ui.components.ConfirmDialog
import io.github.yu1357465.ironlog.ui.components.EmptyState
import io.github.yu1357465.ironlog.ui.components.ExercisePickerSheet
import io.github.yu1357465.ironlog.ui.components.FlatCard
import io.github.yu1357465.ironlog.ui.components.LocalSnackbar
import io.github.yu1357465.ironlog.ui.components.MuscleBadge
import io.github.yu1357465.ironlog.ui.components.SetEditorSheet
import io.github.yu1357465.ironlog.ui.components.TrendLineChart
import io.github.yu1357465.ironlog.ui.components.formatShortDate
import io.github.yu1357465.ironlog.ui.components.formatWeight
import io.github.yu1357465.ironlog.ui.theme.LocalExtraColors
import io.github.yu1357465.ironlog.ui.theme.NumberStyle
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun WorkoutScreen(onOpenPlans: () -> Unit, onOpenSettings: () -> Unit) {
    val vm = appViewModel { WorkoutViewModel(it.repository, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(vm) {
        vm.messages.collect { msg ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(msg.text, msg.actionLabel, withDismissAction = false)
                if (result == SnackbarResult.ActionPerformed) msg.action?.invoke()
            }
        }
    }

    var showAdd by remember { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }
    var replaceTarget by remember { mutableStateOf<ActiveExerciseDetail?>(null) }
    var deleteTarget by remember { mutableStateOf<ActiveExerciseDetail?>(null) }
    var editTarget by remember { mutableStateOf<ActiveExerciseDetail?>(null) }
    var pendingDayId by remember { mutableStateOf<Long?>(null) }
    var confirmReload by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        WorkoutHeader(
            state = state,
            onSelectDay = { dayId ->
                if (dayId == state.context.currentDay?.id) return@WorkoutHeader
                if (state.completedCount > 0) pendingDayId = dayId else vm.selectDay(dayId)
            },
            onSelectPlan = { vm.activatePlan(it) },
            onOpenPlans = onOpenPlans,
            onReload = { confirmReload = true },
            onOpenSettings = onOpenSettings,
        )

        if (!state.loading && state.items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                EmptyState(
                    icon = AppIcons.Mark,
                    title = if (state.context.plan == null) "还没有启用的计划" else "这一天还没有动作",
                    message = if (state.context.plan == null) "可以直接添加动作自由训练，或去创建一个训练计划"
                    else "添加的动作会默认保存进训练日模板，下次自动出现",
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showAdd = true }) { Text("添加动作") }
                            if (state.context.plan == null) OutlinedButton(onClick = onOpenPlans) { Text("管理计划") }
                        }
                    },
                )
            }
        } else {
            ExerciseList(
                state = state,
                vm = vm,
                modifier = Modifier.weight(1f),
                onAdd = { showAdd = true },
                onEdit = { editTarget = it },
                onReplace = { replaceTarget = it },
                onDelete = { item -> if (item.item.inPlan) deleteTarget = item else vm.delete(item, removeFromPlan = false) },
            )
        }

        FinishBar(state = state, onClick = { showFinish = true })
    }

    if (showAdd) {
        ExercisePickerSheet(
            title = "添加动作",
            library = state.library,
            confirmLabel = "添加",
            addToPlanDefault = if (state.context.currentDay != null) state.settings.addToPlanByDefault else null,
            onConfirm = { name, group, addToPlan -> vm.add(name, group, addToPlan) },
            onDismiss = { showAdd = false },
        )
    }
    replaceTarget?.let { target ->
        ExercisePickerSheet(
            title = "更换动作",
            library = state.library,
            confirmLabel = "更换",
            initialName = target.name,
            onConfirm = { name, group, _ -> vm.replace(target, name, group) },
            onDismiss = { replaceTarget = null },
        )
    }
    editTarget?.let { item ->
        SetEditorSheet(
            title = item.name,
            unit = state.settings.unit,
            weightKg = item.item.weightKg,
            sets = item.item.sets,
            reps = item.item.reps,
            onConfirm = { w, s, r -> vm.updateValues(item, w, s, r) },
            onDismiss = { editTarget = null },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${target.name}」") },
            text = { Text("这个动作属于「${state.context.currentDay?.name}」的模板。") },
            confirmButton = {
                TextButton(onClick = { vm.delete(target, removeFromPlan = true); deleteTarget = null }) {
                    Text("同时从模板移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.delete(target, removeFromPlan = false); deleteTarget = null }) { Text("仅本次跳过") }
            },
        )
    }
    pendingDayId?.let { dayId ->
        ConfirmDialog(
            title = "切换训练日？",
            message = "今天已完成 ${state.completedCount} 个动作，切换后这些进度会被清空（不会存档）。",
            confirmLabel = "切换",
            destructive = true,
            onConfirm = { vm.selectDay(dayId) },
            onDismiss = { pendingDayId = null },
        )
    }
    if (confirmReload) {
        ConfirmDialog(
            title = "按模板重新载入？",
            message = "今天的改动和完成状态会被清空，重新按训练日模板生成。",
            confirmLabel = "重新载入",
            onConfirm = { vm.reloadDay() },
            onDismiss = { confirmReload = false },
        )
    }
    if (showFinish) {
        FinishSheet(state = state, onFinish = { vm.finish(it) }, onDismiss = { showFinish = false })
    }
}

@Composable
private fun WorkoutHeader(
    state: WorkoutUiState,
    onSelectDay: (Long) -> Unit,
    onSelectPlan: (io.github.yu1357465.ironlog.data.db.PlanEntity) -> Unit,
    onOpenPlans: () -> Unit,
    onReload: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var planMenu by remember { mutableStateOf(false) }
    var dayMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val ctx = state.context

    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { planMenu = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        ctx.plan?.name ?: "未启用计划",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = planMenu, onDismissRequest = { planMenu = false }) {
                    state.plans.forEach { plan ->
                        DropdownMenuItem(
                            text = { Text(plan.name) },
                            trailingIcon = { if (plan.isActive) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { planMenu = false; if (!plan.isActive) onSelectPlan(plan) },
                        )
                    }
                    if (state.plans.isNotEmpty()) HorizontalDivider()
                    DropdownMenuItem(text = { Text("管理计划…") }, onClick = { planMenu = false; onOpenPlans() })
                }
            }
            Box {
                IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("按模板重新载入") },
                        leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                        enabled = ctx.currentDay != null,
                        onClick = { moreMenu = false; onReload() },
                    )
                    DropdownMenuItem(
                        text = { Text("编辑训练计划") },
                        leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                        onClick = { moreMenu = false; onOpenPlans() },
                    )
                    DropdownMenuItem(
                        text = { Text("设置") },
                        leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                        onClick = { moreMenu = false; onOpenSettings() },
                    )
                }
            }
        }

        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = ctx.days.isNotEmpty()) { dayMenu = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    ctx.currentDay?.name ?: "自由训练",
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (ctx.days.size > 1) {
                    Icon(
                        Icons.Default.KeyboardArrowDown, "选择训练日",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp).size(28.dp),
                    )
                }
            }
            DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                ctx.days.forEachIndexed { i, day ->
                    DropdownMenuItem(
                        text = { Text("${i + 1}. ${day.name}") },
                        trailingIcon = { if (day.id == ctx.currentDay?.id) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { dayMenu = false; onSelectDay(day.id) },
                    )
                }
            }
        }

        if (state.items.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                LinearProgressIndicator(
                    progress = { state.completedCount.toFloat() / state.items.size },
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = LocalExtraColors.current.success,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "${state.completedCount}/${state.items.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}


@Composable
private fun ExerciseList(
    state: WorkoutUiState,
    vm: WorkoutViewModel,
    modifier: Modifier,
    onAdd: () -> Unit,
    onEdit: (ActiveExerciseDetail) -> Unit,
    onReplace: (ActiveExerciseDetail) -> Unit,
    onDelete: (ActiveExerciseDetail) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var list by remember { mutableStateOf(state.items) }
    var dragging by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    LaunchedEffect(state.items) { if (!dragging) list = state.items }

    val listState = rememberLazyListState()
    // 列表从空变为有内容（首次载入、切换训练日）时回到顶部，否则会停在“添加动作”上
    val isEmpty = list.isEmpty()
    LaunchedEffect(isEmpty, state.context.currentDay?.id) { if (!isEmpty) listState.scrollToItem(0) }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = list.indexOfFirst { it.item.id == from.key }
        val toIdx = list.indexOfFirst { it.item.id == to.key }
        if (fromIdx >= 0 && toIdx >= 0) {
            list = list.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(list, key = { it.item.id }) { item ->
            ReorderableItem(reorderState, key = item.item.id) { isDragging ->
                ExerciseCard(
                    item = item,
                    unit = state.settings.unit,
                    hasDay = state.context.currentDay != null,
                    isDragging = isDragging,
                    expanded = item.item.id in expanded,
                    trend = { vm.trend(item.item.exerciseId) },
                    onToggle = { vm.toggleCompleted(item) },
                    onEdit = { onEdit(item) },
                    onToggleExpand = {
                        expanded = if (item.item.id in expanded) expanded - item.item.id else expanded + item.item.id
                    },
                    onReplace = { onReplace(item) },
                    onTogglePlan = { vm.setInPlan(item, !item.item.inPlan) },
                    onDelete = { onDelete(item) },
                    dragHandle = {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.draggableHandle(
                                onDragStarted = {
                                    dragging = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    dragging = false
                                    vm.reorder(list.map { it.item.id })
                                },
                            ),
                        ) {
                            Icon(Icons.Default.DragHandle, "拖动排序", tint = MaterialTheme.colorScheme.outline)
                        }
                    },
                )
            }
        }
        item(key = "add") {
            AddExerciseButton(onAdd)
        }
    }
}

@Composable
private fun AddExerciseButton(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
                )
            }
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text("添加动作", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ExerciseCard(
    item: ActiveExerciseDetail,
    unit: WeightUnit,
    hasDay: Boolean,
    isDragging: Boolean,
    expanded: Boolean,
    trend: () -> kotlinx.coroutines.flow.Flow<List<io.github.yu1357465.ironlog.data.db.EntryDetail>>,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onToggleExpand: () -> Unit,
    onReplace: () -> Unit,
    onTogglePlan: () -> Unit,
    onDelete: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val extra = LocalExtraColors.current
    val done = item.item.completed
    val bg by animateColorAsState(if (done) extra.successContainer else extra.card, label = "cardBg")
    var menu by remember { mutableStateOf(false) }

    FlatCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (isDragging) 1.02f else 1f)
            .then(
                if (!item.item.inPlan && !done) Modifier.border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                    MaterialTheme.shapes.large,
                ) else Modifier
            ),
        containerColor = bg,
        onClick = onToggle,
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, end = 0.dp), verticalAlignment = Alignment.CenterVertically) {
            CheckCircle(done = done, onClick = onToggle)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MuscleBadge(item.muscleGroup, dimmed = done)
                    if (!item.item.inPlan) {
                        Text(
                            "临时",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            IconButton(onClick = onToggleExpand) {
                Icon(
                    Icons.Outlined.ShowChart, "重量趋势",
                    tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多", tint = MaterialTheme.colorScheme.outline) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("更换动作") },
                        leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) },
                        onClick = { menu = false; onReplace() },
                    )
                    if (hasDay) {
                        DropdownMenuItem(
                            text = { Text(if (item.item.inPlan) "移出训练日模板" else "加入训练日模板") },
                            leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                            onClick = { menu = false; onTogglePlan() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
            dragHandle()
        }

        Row(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp).alpha(if (done) 0.6f else 1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ValueTile(formatWeight(item.item.weightKg, unit), unit.label, weight = 1.4f) { onEdit() }
            ValueTile(item.item.sets.toString(), "组") { onEdit() }
            ValueTile(item.item.reps.toString(), "次") { onEdit() }
        }

        AnimatedVisibility(visible = expanded) {
            TrendPanel(trend, unit)
        }
    }
}

@Composable
private fun CheckCircle(done: Boolean, onClick: () -> Unit) {
    val extra = LocalExtraColors.current
    if (done) {
        Icon(
            Icons.Filled.CheckCircle, "已完成",
            tint = extra.success,
            modifier = Modifier.size(30.dp).clickable(onClick = onClick),
        )
    } else {
        Box(
            Modifier
                .size(30.dp)
                .padding(2.dp)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClick = onClick),
        )
    }
}

@Composable
private fun RowScope.ValueTile(value: String, unit: String, weight: Float = 1f, onClick: () -> Unit) {
    Row(
        Modifier
            .weight(weight)
            .height(52.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, style = NumberStyle, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(4.dp))
        Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrendPanel(
    trend: () -> kotlinx.coroutines.flow.Flow<List<io.github.yu1357465.ironlog.data.db.EntryDetail>>,
    unit: WeightUnit,
) {
    val flow = remember { trend() }
    val entries by flow.collectAsStateWithLifecycle(initialValue = null)
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        val list = entries
        when {
            list == null -> Spacer(Modifier.height(140.dp))
            list.isEmpty() -> Text(
                "还没有历史记录，完成一次训练后这里会显示重量趋势",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            else -> {
                val points = list.reversed().map {
                    ChartPoint(formatShortDate(it.performedAt), unit.fromKg(it.entry.weightKg), formatWeight(it.entry.weightKg, unit))
                }
                Text("最近 ${points.size} 次 (${unit.label})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TrendLineChart(points)
            }
        }
    }
}

@Composable
private fun FinishBar(state: WorkoutUiState, onClick: () -> Unit) {
    val enabled = state.items.isNotEmpty()
    val allDone = enabled && state.remaining == 0
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allDone) LocalExtraColors.current.success else MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                when {
                    !enabled -> "今天还没有动作"
                    allDone -> "完成训练，存档"
                    else -> "结束训练 · 还剩 ${state.remaining} 个"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinishSheet(state: WorkoutUiState, onFinish: (markAll: Boolean) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(action: () -> Unit) {
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    val unit = state.settings.unit
    val doneItems = state.items.filter { it.item.completed }
    val volume = doneItems.sumOf { it.item.weightKg * it.item.sets * it.item.reps }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()) {
            if (state.remaining == 0) {
                Text("训练完成 🎉", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${doneItems.size} 个动作 · 总容量 ${io.github.yu1357465.ironlog.ui.components.formatVolume(volume, unit)} ${unit.label}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { close { onFinish(false) } }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("存档并进入下一天") }
            } else {
                Text("还有 ${state.remaining} 个动作没做完", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text("要怎么处理剩下的动作？", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { close { onFinish(true) } }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("全部标记完成并存档") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { close { onFinish(false) } },
                    enabled = doneItems.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (doneItems.isEmpty()) "还没有完成的动作" else "只存档已完成的 ${doneItems.size} 个") }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("继续训练") }
            Spacer(Modifier.height(8.dp))
        }
    }
}
