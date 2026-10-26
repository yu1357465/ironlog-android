package io.github.yu1357465.ironlog.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.yu1357465.ironlog.data.AppSettings
import io.github.yu1357465.ironlog.data.FitnessRepository
import io.github.yu1357465.ironlog.data.SettingsRepository
import io.github.yu1357465.ironlog.data.model.MuscleGroup
import io.github.yu1357465.ironlog.domain.Lift
import io.github.yu1357465.ironlog.domain.RadarMode
import io.github.yu1357465.ironlog.domain.Strength
import io.github.yu1357465.ironlog.domain.StrengthReport
import io.github.yu1357465.ironlog.ui.appViewModel
import io.github.yu1357465.ironlog.ui.components.EmptyState
import io.github.yu1357465.ironlog.ui.components.FlatCard
import io.github.yu1357465.ironlog.ui.components.MuscleBadge
import io.github.yu1357465.ironlog.ui.components.RadarChart
import io.github.yu1357465.ironlog.ui.components.ScreenHeader
import io.github.yu1357465.ironlog.ui.components.SectionTitle
import io.github.yu1357465.ironlog.ui.components.formatVolume
import io.github.yu1357465.ironlog.ui.components.formatWeight
import io.github.yu1357465.ironlog.ui.theme.NumberStyle
import io.github.yu1357465.ironlog.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class StatsUiState(
    val loading: Boolean = true,
    val mode: RadarMode = RadarMode.OVERALL,
    val report: StrengthReport? = null,
    val settings: AppSettings = AppSettings(),
    val sessionCount: Int = 0,
    val sessionsLast30Days: Int = 0,
    val totalVolumeKg: Double = 0.0,
)

class StatsViewModel(repo: FitnessRepository, settings: SettingsRepository) : ViewModel() {
    private val mode = MutableStateFlow(RadarMode.OVERALL)

    val state = combine(repo.allEntries, settings.settings, mode) { entries, s, m ->
        val lifts = entries.map { Lift(it.entry.exerciseId, it.name, it.muscleGroup, it.entry.weightKg, it.entry.reps, it.entry.sets, it.performedAt) }
        val monthAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis
        val sessions = entries.groupBy { it.entry.sessionId }
        StatsUiState(
            loading = false,
            mode = m,
            report = if (lifts.isEmpty()) null else Strength.analyze(lifts, m, s.body.bodyWeightKg.toDouble(), s.body.apeIndex.toDouble()),
            settings = s,
            sessionCount = sessions.size,
            sessionsLast30Days = sessions.values.count { it.first().performedAt >= monthAgo },
            totalVolumeKg = entries.sumOf { it.entry.weightKg * it.entry.sets * it.entry.reps },
        )
    }.flowOn(Dispatchers.Default) // 1RM/评分计算不占用主线程
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setMode(m: RadarMode) { mode.value = m }
}

@Composable
fun StatsScreen(onOpenSettings: () -> Unit) {
    val vm = appViewModel { StatsViewModel(it.repository, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showHelp by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "分析",
            actions = { IconButton(onClick = { showHelp = true }) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, "算法说明") } },
        )
        val report = state.report
        if (!state.loading && report == null) {
            EmptyState(Icons.Outlined.Insights, "数据还不够", "完成几次训练后，这里会显示各肌群的力量评分和训练结构")
            return@Column
        }
        if (report == null) return@Column

        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            val unit = state.settings.unit
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryTile("累计训练", "${state.sessionCount}", "次", Modifier.weight(1f))
                SummaryTile("近 30 天", "${state.sessionsLast30Days}", "次", Modifier.weight(1f))
                SummaryTile("总容量", formatVolume(state.totalVolumeKg, unit), unit.label, Modifier.weight(1f))
            }

            SectionTitle("肌群力量评分")
            FlatCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        RadarMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = state.mode == m,
                                onClick = { vm.setMode(m) },
                                shape = SegmentedButtonDefaults.itemShape(i, RadarMode.entries.size),
                            ) { Text(m.label) }
                        }
                    }
                    Text(
                        (if (report.recentOnly) "近 ${Strength.RECENT_DAYS} 天" else "全部历史（近期无训练）") +
                            " · ${state.mode.repRange.first}–${state.mode.repRange.last} 次的组 · 100 分≈进阶水平",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    RadarChart(
                        labels = MuscleGroup.radarGroups.map { it.label },
                        values = MuscleGroup.radarGroups.map { report.scores[it] ?: 0f },
                        color = when (state.mode) {
                            RadarMode.OVERALL -> MaterialTheme.colorScheme.primary
                            RadarMode.STRENGTH -> Color(0xFFE53935)
                            RadarMode.ENDURANCE -> Color(0xFF2E9E4F)
                        },
                    )
                    BodyInsight(state.settings, onOpenSettings)
                }
            }

            SectionTitle("训练结构（按组数）")
            FlatCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                EnergyBar(report)
            }

            if (report.bestLifts.isNotEmpty()) {
                SectionTitle("估算 1RM 排行")
                FlatCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    report.bestLifts.forEachIndexed { i, lift ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(24.dp))
                            Text(lift.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            MuscleBadge(lift.group)
                            Spacer(Modifier.width(12.dp))
                            Text("${formatWeight(lift.estimated1RmKg, unit)} ${unit.label}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("评分是怎么算的") },
            text = {
                Text(
                    "1. 每组训练先折算成单次极限重量（1RM）：10 次以内用 Brzycki 公式，超过 10 次改用 Epley（Brzycki 在高次数下会严重高估）。\n\n" +
                        "2. 每个动作有自己的满分标准，按动作名识别：例如杠铃卧推 1RM 达到体重 1.5 倍、深蹲 1.75 倍、杠铃弯举 0.7 倍为满分；哑铃动作按单只重量计，标准相应更低。认不出的动作按所属肌群的默认标准。100 分大致相当于健身房里的“进阶”水平。\n\n" +
                        "3. 引体向上、双杠臂屈伸会把体重计入负荷；悬垂举腿、卷腹、平板支撑等不负重的动作按次数（秒数）评分。\n\n" +
                        "4. 肌群得分取该肌群表现最好的动作，只统计最近 ${Strength.RECENT_DAYS} 天，反映当前水平；近期没有训练时才使用全部历史。\n\n" +
                        "5. 填写身高和臂展后，臂展较长的人推类动作得分上调、拉类（含硬拉）下调，修正幅度最多 8%。\n\n" +
                        "6. 三种模式只是筛选不同次数区间的组：力量 1–6 次、综合 1–15 次、耐力 10–30 次。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("知道了") } },
        )
    }
}

@Composable
private fun SummaryTile(label: String, value: String, unit: String, modifier: Modifier) {
    FlatCard(modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = NumberStyle)
                Spacer(Modifier.width(2.dp))
                Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun BodyInsight(settings: AppSettings, onOpenSettings: () -> Unit) {
    val body = settings.body
    val ape = body.apeIndex
    val text = when {
        body.bodyWeightKg <= 0f -> "还没有填写体重，暂按 70kg 计算。填写身体数据后评分会更准确。"
        body.heightCm <= 0f || body.wingspanCm <= 0f -> "填写身高和臂展后，可以按你的杠杆特点修正推/拉评分。"
        ape > 1.02f -> String.format("Ape Index %.2f：臂展偏长，拉类动作占优，推类评分已上调补偿。", ape)
        ape < 0.98f -> String.format("Ape Index %.2f：臂展偏短，推类动作占优，拉类评分已上调补偿。", ape)
        else -> String.format("Ape Index %.2f：身材比例均衡，按标准模型评分。", ape)
    }
    val needsInput = body.bodyWeightKg <= 0f || body.heightCm <= 0f || body.wingspanCm <= 0f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
        if (needsInput) TextButton(onClick = onOpenSettings) { Text("去填写") }
    }
}

@Composable
private fun EnergyBar(report: StrengthReport) {
    val d = report.distribution
    val parts = listOf(
        Triple("力量 1–5", d.strength, Color(0xFFE53935)),
        Triple("增肌 6–12", d.hypertrophy, Color(0xFF3F6FD8)),
        Triple("耐力 13+", d.endurance, Color(0xFF2E9E4F)),
    )
    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
            parts.filter { it.second > 0 }.forEach { (_, count, color) ->
                Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(color))
            }
            if (d.total == 0) Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            parts.forEach { (label, count, color) ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(color, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "${(d.fraction(count) * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
            }
        }
    }
}
