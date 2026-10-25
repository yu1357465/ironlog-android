package com.example.fitness_plan.ui.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitness_plan.data.db.PlanDayEntity
import com.example.fitness_plan.data.db.PlanEntity
import com.example.fitness_plan.ui.appViewModel
import com.example.fitness_plan.ui.components.ConfirmDialog
import com.example.fitness_plan.ui.components.EmptyState
import com.example.fitness_plan.ui.components.FlatCard
import com.example.fitness_plan.ui.components.LocalSnackbar
import com.example.fitness_plan.ui.components.ScreenHeader
import com.example.fitness_plan.ui.components.TextInputDialog

private sealed interface PlanDialog {
    data object NewPlan : PlanDialog
    data class RenamePlan(val plan: PlanEntity) : PlanDialog
    data class DeletePlan(val plan: PlanEntity) : PlanDialog
    data class AddDay(val plan: PlanEntity) : PlanDialog
    data class RenameDay(val day: PlanDayEntity) : PlanDialog
    data class DeleteDay(val day: PlanDayEntity) : PlanDialog
    data class CopyDay(val day: PlanDayEntity) : PlanDialog
}

@Composable
fun PlansScreen(onBack: () -> Unit, onOpenDay: (Long) -> Unit) {
    val vm = appViewModel { PlansViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    var dialog by remember { mutableStateOf<PlanDialog?>(null) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("训练计划", onBack = onBack)
            if (!state.loading && state.plans.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.EventNote,
                    title = "还没有计划",
                    message = "计划由若干训练日组成，完成一天后会自动轮到下一天",
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.plans, key = { it.plan.id }) { pw ->
                    val index = state.plans.indexOf(pw)
                    PlanCard(
                        pw = pw,
                        counts = state.exerciseCounts,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.plans.lastIndex,
                        onMove = { delta ->
                            val ids = state.plans.map { it.plan.id }.toMutableList()
                            ids.add(index + delta, ids.removeAt(index))
                            vm.reorderPlans(ids)
                        },
                        onActivate = { vm.activate(pw.plan) },
                        onRename = { dialog = PlanDialog.RenamePlan(pw.plan) },
                        onDelete = { dialog = PlanDialog.DeletePlan(pw.plan) },
                        onAddDay = { dialog = PlanDialog.AddDay(pw.plan) },
                        onOpenDay = onOpenDay,
                        onMoveDay = { day, delta ->
                            val ids = pw.days.map { it.id }.toMutableList()
                            val i = ids.indexOf(day.id)
                            ids.add(i + delta, ids.removeAt(i))
                            vm.reorderDays(pw.plan.id, ids)
                        },
                        onRenameDay = { dialog = PlanDialog.RenameDay(it) },
                        onCopyDay = { dialog = PlanDialog.CopyDay(it) },
                        onDeleteDay = { dialog = PlanDialog.DeleteDay(it) },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { dialog = PlanDialog.NewPlan },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("新建计划") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        )
    }

    when (val d = dialog) {
        null -> Unit
        PlanDialog.NewPlan -> TextInputDialog("新建计划", "计划名称", confirmLabel = "创建", onConfirm = vm::createPlan, onDismiss = { dialog = null })
        is PlanDialog.RenamePlan -> TextInputDialog("重命名计划", "计划名称", d.plan.name, onConfirm = { vm.renamePlan(d.plan, it) }, onDismiss = { dialog = null })
        is PlanDialog.DeletePlan -> ConfirmDialog(
            "删除「${d.plan.name}」？",
            "计划里的所有训练日都会删除，已存档的历史记录不受影响。" + if (d.plan.isActive) "\n这是正在使用的计划，今天的训练台也会清空。" else "",
            "删除", onConfirm = { vm.deletePlan(d.plan) }, onDismiss = { dialog = null }, destructive = true,
        )
        is PlanDialog.AddDay -> TextInputDialog("添加训练日", "名称，如：肩部日", confirmLabel = "添加", onConfirm = { vm.addDay(d.plan, it) }, onDismiss = { dialog = null })
        is PlanDialog.RenameDay -> TextInputDialog("重命名训练日", "名称", d.day.name, onConfirm = { vm.renameDay(d.day, it) }, onDismiss = { dialog = null })
        is PlanDialog.DeleteDay -> ConfirmDialog(
            "删除「${d.day.name}」？", "这一天的模板动作会一起删除。", "删除",
            onConfirm = { vm.deleteDay(d.day) }, onDismiss = { dialog = null }, destructive = true,
        )
        is PlanDialog.CopyDay -> {
            val targets = state.plans.map { it.plan }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("复制「${d.day.name}」到…") },
                text = {
                    Column {
                        targets.forEach { plan ->
                            Text(
                                plan.name + if (plan.id == d.day.planId) "（本计划）" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.copyDay(d.day, plan); dialog = null }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text("取消") } },
            )
        }
    }
}

@Composable
private fun PlanCard(
    pw: PlanWithDays,
    counts: Map<Long, Int>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddDay: () -> Unit,
    onOpenDay: (Long) -> Unit,
    onMoveDay: (PlanDayEntity, Int) -> Unit,
    onRenameDay: (PlanDayEntity) -> Unit,
    onCopyDay: (PlanDayEntity) -> Unit,
    onDeleteDay: (PlanDayEntity) -> Unit,
) {
    val plan = pw.plan
    var menu by remember { mutableStateOf(false) }
    FlatCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (plan.isActive) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "使用中", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                Text("${pw.days.size} 个训练日", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!plan.isActive) TextButton(onClick = onActivate) { Text("启用") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("重命名") }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) }, onClick = { menu = false; onRename() })
                    if (canMoveUp) DropdownMenuItem(text = { Text("上移") }, leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) }, onClick = { menu = false; onMove(-1) })
                    if (canMoveDown) DropdownMenuItem(text = { Text("下移") }, leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) }, onClick = { menu = false; onMove(1) })
                    DropdownMenuItem(
                        text = { Text("删除计划", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        pw.days.forEachIndexed { i, day ->
            DayRow(
                index = i,
                day = day,
                count = counts[day.id] ?: 0,
                isCurrent = plan.isActive && plan.currentDayId == day.id,
                canMoveUp = i > 0,
                canMoveDown = i < pw.days.lastIndex,
                onOpen = { onOpenDay(day.id) },
                onMove = { onMoveDay(day, it) },
                onRename = { onRenameDay(day) },
                onCopy = { onCopyDay(day) },
                onDelete = { onDeleteDay(day) },
            )
        }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onAddDay).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("添加训练日", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DayRow(
    index: Int,
    day: PlanDayEntity,
    count: Int,
    isCurrent: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMove: (Int) -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).background(
                if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${index + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(day.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (count == 0) "还没有动作" else "$count 个动作") + if (isCurrent) " · 下次训练" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多", tint = MaterialTheme.colorScheme.outline) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("重命名") }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) }, onClick = { menu = false; onRename() })
                if (canMoveUp) DropdownMenuItem(text = { Text("上移") }, leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) }, onClick = { menu = false; onMove(-1) })
                if (canMoveDown) DropdownMenuItem(text = { Text("下移") }, leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) }, onClick = { menu = false; onMove(1) })
                DropdownMenuItem(text = { Text("复制到…") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { menu = false; onCopy() })
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 12.dp))
    }
}
