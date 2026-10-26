package com.example.fitness_plan.ui.plans

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fitness_plan.data.FitnessRepository
import com.example.fitness_plan.data.PlanShare
import com.example.fitness_plan.ui.components.MuscleDot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 分享面板：二维码 + 「包含重量」开关 + 复制 / 保存图片 / 发送图片 / 导出文件。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanShareSheet(vm: PlansViewModel, state: ShareState, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val plan = state.plan
    val subtitle = "${plan.days.size} 天 · ${plan.exerciseCount} 个动作" + if (state.includeWeights) " · 含重量" else ""

    // 二维码在后台生成；null 表示内容超出二维码容量
    val qr by produceState<Result<Bitmap?>?>(null, state.code) {
        value = Result.success(withContext(Dispatchers.Default) { QrCodes.encode(state.code, 720) })
    }

    suspend fun poster(): Bitmap? = withContext(Dispatchers.Default) {
        QrCodes.encode(state.code, 1000)?.let { QrCodes.poster(it, plan.name, subtitle) }
    }

    ModalBottomSheet(onDismissRequest = vm::closeShare, sheetState = sheetState) {
      Box {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(plan.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = qr?.getOrNull()
                when {
                    qr == null -> CircularProgressIndicator()
                    bitmap == null -> Text(
                        "计划内容太多，放不进二维码。\n请用下面的「复制分享码」或「导出文件」分享。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF68717B),
                        textAlign = TextAlign.Center,
                    )
                    else -> Image(
                        bitmap.asImageBitmap(),
                        contentDescription = "计划二维码",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clickable { vm.setIncludeWeights(!state.includeWeights) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("包含重量", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (state.includeWeights) "对方会看到你模板里的默认重量" else "对方导入后重量为 0，第一次训练时自己填",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.includeWeights, onCheckedChange = vm::setIncludeWeights)
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShareAction(Icons.Outlined.ContentCopy, "复制分享码", Modifier.weight(1f)) {
                    clipboard.setText(AnnotatedString(PlanShare.shareText(plan)))
                    vm.notify("已复制，可以直接粘贴到聊天中发送")
                }
                val canImage = qr?.getOrNull() != null
                ShareAction(Icons.Outlined.Image, "保存图片", Modifier.weight(1f), enabled = canImage) {
                    vm.launchPoster(::poster) { bmp -> vm.savePoster(bmp, notify = true) }
                }
                ShareAction(Icons.Outlined.Send, "发送图片", Modifier.weight(1f), enabled = canImage) {
                    vm.launchPoster(::poster) { bmp ->
                        vm.savePoster(bmp, notify = false) { uri ->
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("image/png")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(send, "发送计划二维码"))
                        }
                    }
                }
                ShareAction(Icons.Outlined.Description, "导出文件", Modifier.weight(1f)) { vm.exportFile() }
            }
            Spacer(Modifier.height(20.dp))
        }
        // 提示显示在面板顶部，不挡住下面的按钮
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).padding(horizontal = 8.dp))
      }
    }
}

@Composable
private fun ShareAction(icon: ImageVector, label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 72.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** 粘贴分享码：自动带入剪贴板里的分享码。 */
@Composable
fun PasteCodeDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val initial = remember {
        clipboard.getText()?.text?.takeIf { PlanShare.PREFIX in it }.orEmpty()
    }
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("粘贴分享码") },
        text = {
            Column {
                Text(
                    if (initial.isNotEmpty()) "已自动粘贴剪贴板中的分享码" else "把朋友发来的整段文字粘贴到这里",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("FP1:…") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text); onDismiss() }) { Text("识别") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 导入前预览。 */
@Composable
fun PlanImportDialog(preview: FitnessRepository.PlanImportPreview, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val plan = preview.plan
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入「${plan.name}」") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${plan.days.size} 天 · ${plan.exerciseCount} 个动作" + if (plan.includesWeights) " · 含重量" else " · 不含重量",
                    style = MaterialTheme.typography.titleSmall,
                )
                plan.days.forEachIndexed { i, day ->
                    Column {
                        Text("${i + 1}. ${day.name}", style = MaterialTheme.typography.bodyMedium)
                        day.exercises.forEach { e ->
                            Row(Modifier.padding(start = 16.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                MuscleDot(e.group)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${e.name}  ${e.sets}×${e.reps}" + (e.weightKg?.let { "  ${com.example.fitness_plan.ui.components.formatNumber(it)}kg" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (preview.newExercises.isNotEmpty()) {
                    Text(
                        "将在动作库中新建 ${preview.newExercises.size} 个动作：" + preview.newExercises.take(10).joinToString("、") +
                            if (preview.newExercises.size > 10) " 等" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    (if (preview.finalName != plan.name) "已有同名计划，将命名为「${preview.finalName}」。" else "") +
                        "作为新计划添加，不会影响现有计划和历史记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("导入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
