package com.example.fitness_plan.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.fitness_plan.BuildConfig
import com.example.fitness_plan.data.AppSettings
import com.example.fitness_plan.data.BackupManager
import com.example.fitness_plan.data.BodyMetrics
import com.example.fitness_plan.data.FitnessRepository
import com.example.fitness_plan.data.SettingsRepository
import com.example.fitness_plan.data.model.WeightUnit
import com.example.fitness_plan.ui.UiMessage
import com.example.fitness_plan.ui.appViewModel
import com.example.fitness_plan.ui.components.ConfirmDialog
import com.example.fitness_plan.ui.components.FlatCard
import com.example.fitness_plan.ui.components.LocalSnackbar
import com.example.fitness_plan.ui.components.ScreenHeader
import com.example.fitness_plan.ui.components.SectionTitle
import com.example.fitness_plan.ui.components.formatNumber
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: FitnessRepository,
    private val settingsRepo: SettingsRepository,
    private val backup: BackupManager,
) : ViewModel() {
    val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** 正在执行的导出/恢复，避免重复点击。 */
    var busy by mutableStateOf(false)
        private set

    fun setUnit(unit: WeightUnit) = viewModelScope.launch { settingsRepo.setUnit(unit) }
    fun setAddToPlan(value: Boolean) = viewModelScope.launch { settingsRepo.setAddToPlanByDefault(value) }
    fun saveBody(body: BodyMetrics) = viewModelScope.launch {
        settingsRepo.setBodyMetrics(body)
        _messages.send(UiMessage("身体数据已保存"))
    }

    private fun runTask(block: suspend () -> UiMessage) = viewModelScope.launch {
        if (busy) return@launch
        busy = true
        val msg = try { block() } catch (e: Exception) { UiMessage("失败：${e.message}") }
        busy = false
        _messages.send(msg)
    }

    fun exportBackup(open: (Uri, String) -> Unit) = runTask {
        val uri = backup.exportBackup()
        UiMessage("已备份到 下载/${BackupManager.FOLDER}", "打开") { open(uri, "application/json") }
    }

    fun exportCsv(open: (Uri, String) -> Unit) = runTask {
        val uri = backup.exportCsv()
        UiMessage("报表已导出到 下载/${BackupManager.FOLDER}", "打开") { open(uri, "text/csv") }
    }

    fun restore(uri: Uri) = runTask {
        backup.restore(uri)
        UiMessage("恢复完成")
    }

    fun clearHistory() = runTask {
        repo.clearHistory()
        UiMessage("历史记录已清空")
    }
}

@Composable
fun SettingsScreen(onOpenPlans: () -> Unit, onOpenLibrary: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it.repository, it.settings, it.backup) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.messages.collect { msg ->
            scope.launch {
                if (snackbar.showSnackbar(msg.text, msg.actionLabel) == SnackbarResult.ActionPerformed) msg.action?.invoke()
            }
        }
    }
    val open: (Uri, String) -> Unit = { uri, mime ->
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }.onFailure { scope.launch { snackbar.showSnackbar("没有可以打开该文件的应用") } }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("我的")
        val s = settings ?: return@Column
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SectionTitle("训练")
            FlatCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                NavRow(Icons.Outlined.EventNote, "训练计划", "管理计划和每个训练日的动作", onOpenPlans)
                Divider()
                NavRow(Icons.Outlined.FitnessCenter, "动作库", "新建、改名、调整肌群分类", onOpenLibrary)
            }

            SectionTitle("偏好")
            FlatCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("重量单位", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow(Modifier.width(160.dp)) {
                        WeightUnit.entries.forEachIndexed { i, u ->
                            SegmentedButton(
                                selected = s.unit == u,
                                onClick = { vm.setUnit(u) },
                                shape = SegmentedButtonDefaults.itemShape(i, WeightUnit.entries.size),
                            ) { Text(u.label) }
                        }
                    }
                }
                Divider()
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setAddToPlan(!s.addToPlanByDefault) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("新增动作默认加入模板", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "关闭后，训练页新增的动作默认只用于当次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = s.addToPlanByDefault, onCheckedChange = vm::setAddToPlan)
                }
            }

            SectionTitle("身体数据（用于力量评分）")
            BodyMetricsCard(s, vm::saveBody)

            SectionTitle("数据")
            FlatCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ActionRow(Icons.Outlined.Backup, "备份全部数据", "保存为 JSON 到 下载/${BackupManager.FOLDER}", enabled = !vm.busy) { vm.exportBackup(open) }
                Divider()
                ActionRow(Icons.Outlined.Restore, "从备份恢复", "会覆盖当前所有数据", enabled = !vm.busy) {
                    restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                }
                Divider()
                ActionRow(Icons.Outlined.TableChart, "导出训练报表", "CSV 格式，可用 Excel 打开", enabled = !vm.busy) { vm.exportCsv(open) }
                Divider()
                ActionRow(
                    Icons.Outlined.DeleteSweep, "清空历史记录", "计划和动作库会保留",
                    tint = MaterialTheme.colorScheme.error, enabled = !vm.busy,
                ) { confirmClear = true }
            }

            Text(
                "版本 ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    pendingRestore?.let { uri ->
        ConfirmDialog(
            "从备份恢复？", "当前所有计划、动作和历史记录都会被备份文件中的内容替换。建议先备份一次当前数据。",
            "恢复", onConfirm = { vm.restore(uri) }, onDismiss = { pendingRestore = null }, destructive = true,
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            "清空所有历史记录？", "此操作无法撤销。建议先备份。", "清空",
            onConfirm = vm::clearHistory, onDismiss = { confirmClear = false }, destructive = true,
        )
    }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ActionRow(icon, title, subtitle, trailing = true, onClick = onClick)
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    trailing: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun BodyMetricsCard(settings: AppSettings, onSave: (BodyMetrics) -> Unit) {
    val body = settings.body
    fun show(v: Float) = if (v > 0) formatNumber(v.toDouble()) else ""
    var height by remember(body) { mutableStateOf(show(body.heightCm)) }
    var wingspan by remember(body) { mutableStateOf(show(body.wingspanCm)) }
    var weight by remember(body) { mutableStateOf(show(body.bodyWeightKg)) }
    val focus = LocalFocusManager.current

    val h = height.toFloatOrNull() ?: 0f
    val w = wingspan.toFloatOrNull() ?: 0f
    val bw = weight.toFloatOrNull() ?: 0f
    val valid = h in 0f..250f && w in 0f..260f && bw in 0f..300f
    val changed = h != body.heightCm || w != body.wingspanCm || bw != body.bodyWeightKg

    FlatCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("身高 cm", height, { height = it }, Modifier.weight(1f))
                NumberField("臂展 cm", wingspan, { wingspan = it }, Modifier.weight(1f))
                NumberField("体重 kg", weight, { weight = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (!valid) "数值似乎超出正常范围，请检查单位" else "臂展：双臂水平伸直时，两手中指指尖的距离",
                style = MaterialTheme.typography.bodySmall,
                color = if (!valid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = { focus.clearFocus(); onSave(BodyMetrics(h, w, bw)) },
                enabled = valid && changed,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
