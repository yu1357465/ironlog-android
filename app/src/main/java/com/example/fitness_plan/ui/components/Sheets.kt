package com.example.fitness_plan.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fitness_plan.data.db.ExerciseEntity
import com.example.fitness_plan.data.model.MuscleGroup
import com.example.fitness_plan.data.model.WeightUnit
import com.example.fitness_plan.ui.theme.NumberStyle

/**
 * 选择或新建动作：输入时联想动作库；名字命中已有动作时肌群锁定为库中的分类，
 * 避免同名动作出现两种分类、污染雷达图数据。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExercisePickerSheet(
    title: String,
    library: List<ExerciseEntity>,
    confirmLabel: String,
    onConfirm: (name: String, group: MuscleGroup, addToPlan: Boolean) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    /** 非 null 时显示“同时加入训练日模板”开关。 */
    addToPlanDefault: Boolean? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length))) }
    var group by remember { mutableStateOf(MuscleGroup.CHEST) }
    var addToPlan by remember { mutableStateOf(addToPlanDefault ?: false) }
    val focus = remember { FocusRequester() }

    val query = name.text.trim()
    val match = library.firstOrNull { it.name == query }
    val suggestions = if (query.isEmpty()) library.take(5)
    else library.filter { it.name.contains(query, ignoreCase = true) && it.name != query }.take(6)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("动作名称") },
                placeholder = { Text("如：杠铃划船") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )

            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (query.isEmpty()) "动作库" else "相似动作",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column {
                    suggestions.forEach { ex ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { name = TextFieldValue(ex.name, TextRange(ex.name.length)) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MuscleDot(ex.muscleGroup)
                            Spacer(Modifier.width(12.dp))
                            Text(ex.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(ex.muscleGroup.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            if (match != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("已在动作库中", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    MuscleBadge(match.muscleGroup)
                }
            } else {
                Text("目标肌群", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuscleGroup.entries.forEach { g ->
                        FilterChip(selected = g == group, onClick = { group = g }, label = { Text(g.label) })
                    }
                }
            }

            if (addToPlanDefault != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { addToPlan = !addToPlan }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("加入当前训练日模板", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (addToPlan) "以后每次练这一天都会出现" else "仅本次训练临时加入",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = addToPlan, onCheckedChange = { addToPlan = it })
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onConfirm(query, match?.muscleGroup ?: group, addToPlan); onDismiss() },
                enabled = query.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(confirmLabel) }
            Spacer(Modifier.height(16.dp))
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** 一次编辑重量/组数/次数；带步进按钮，训练间隙单手也好操作。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SetEditorSheet(
    title: String,
    unit: WeightUnit,
    weightKg: Double,
    sets: Int,
    reps: Int,
    onConfirm: (weightKg: Double, sets: Int, reps: Int) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "保存",
    header: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {},
    footer: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var weightText by remember { mutableStateOf(formatWeight(weightKg, unit)) }
    var setsText by remember { mutableStateOf(sets.toString()) }
    var repsText by remember { mutableStateOf(reps.toString()) }
    val weightStep = if (unit == WeightUnit.KG) 2.5 else 5.0

    val weight = weightText.toDoubleOrNull()
    val setsVal = setsText.toIntOrNull()
    val repsVal = repsText.toIntOrNull()
    val valid = weight != null && weight >= 0 && setsVal != null && setsVal in 1..99 && repsVal != null && repsVal in 1..999

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))
            header()
            StepperRow(
                label = "重量 (${unit.label})",
                text = weightText,
                onTextChange = { weightText = it.filter { c -> c.isDigit() || c == '.' } },
                onMinus = { weightText = formatNumber(((weight ?: 0.0) - weightStep).coerceAtLeast(0.0)) },
                onPlus = { weightText = formatNumber((weight ?: 0.0) + weightStep) },
                decimal = true,
            )
            StepperRow(
                label = "组数",
                text = setsText,
                onTextChange = { setsText = it.filter(Char::isDigit) },
                onMinus = { setsText = ((setsVal ?: 1) - 1).coerceAtLeast(1).toString() },
                onPlus = { setsText = ((setsVal ?: 0) + 1).coerceAtMost(99).toString() },
            )
            StepperRow(
                label = "次数",
                text = repsText,
                onTextChange = { repsText = it.filter(Char::isDigit) },
                onMinus = { repsText = ((repsVal ?: 1) - 1).coerceAtLeast(1).toString() },
                onPlus = { repsText = ((repsVal ?: 0) + 1).coerceAtMost(999).toString() },
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onConfirm(unit.toKg(weight!!), setsVal!!, repsVal!!); onDismiss() },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(confirmLabel) }
            footer()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    decimal: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Remove, "减少") }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = NumberStyle.copy(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            modifier = Modifier.width(96.dp).padding(horizontal = 8.dp),
        )
        FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Add, "增加") }
    }
}
