package io.github.yu1357465.ironlog.data

import io.github.yu1357465.ironlog.data.model.MuscleGroup
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/** 可分享的训练计划：只含名称和模板，不含任何历史记录。 */
data class SharedPlan(val name: String, val days: List<SharedDay>) {
    val exerciseCount: Int get() = days.sumOf { it.exercises.size }
    val includesWeights: Boolean get() = days.any { d -> d.exercises.any { it.weightKg != null } }
}

data class SharedDay(val name: String, val exercises: List<SharedExercise>)

/** [weightKg] 为 null 表示分享时没有包含重量。 */
data class SharedExercise(
    val name: String,
    val group: MuscleGroup,
    val sets: Int,
    val reps: Int,
    val weightKg: Double?,
)

class PlanShareException(message: String) : Exception(message)

/**
 * 计划分享格式。
 * - 文件：可读的 JSON（[toJson]）。
 * - 分享码：`FP1:` + Base64URL(Deflate(JSON))，二维码与文字分享都用它；
 *   解码时会在任意文字里查找分享码，所以整段聊天消息直接粘贴也能识别。
 */
object PlanShare {

    const val PREFIX = "FP1:"
    private const val TYPE = "fitness-plan"
    private const val VERSION = 1
    private val CODE_REGEX = Regex("""FP1:[A-Za-z0-9_-]+""")

    // 防止异常数据：解压上限、数量和数值范围
    private const val MAX_JSON_BYTES = 256 * 1024
    private const val MAX_DAYS = 30
    private const val MAX_EXERCISES_PER_DAY = 60
    private const val MAX_NAME_LENGTH = 60

    // ---------------- 编码 ----------------

    fun toJson(plan: SharedPlan): JSONObject = JSONObject().apply {
        put("type", TYPE)
        put("version", VERSION)
        put("name", plan.name)
        put("days", JSONArray().also { days ->
            plan.days.forEach { day ->
                days.put(JSONObject().apply {
                    put("name", day.name)
                    put("exercises", JSONArray().also { list ->
                        day.exercises.forEach { e ->
                            list.put(JSONObject().apply {
                                put("name", e.name)
                                put("group", e.group.name)
                                put("sets", e.sets)
                                put("reps", e.reps)
                                e.weightKg?.let { put("weightKg", it) }
                            })
                        }
                    })
                })
            }
        })
    }

    fun encode(plan: SharedPlan): String {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        val compressed = try {
            deflater.setInput(toJson(plan).toString().toByteArray())
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            out.toByteArray()
        } finally {
            deflater.end()
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    /** 发到聊天里的完整文字：带说明，对方整段复制后即可导入。 */
    fun shareText(plan: SharedPlan): String =
        "【IronLog 训练计划】${plan.name}（${plan.days.size} 天，${plan.exerciseCount} 个动作）\n" +
            "复制这整段文字，打开 IronLog→ 训练计划 → 导入 → 粘贴分享码\n" +
            encode(plan)

    // ---------------- 解码 ----------------

    /** 解析分享码、包含分享码的任意文字，或计划 JSON 文件内容。 */
    fun decode(text: String): SharedPlan {
        val trimmed = text.trim().removePrefix("﻿")
        val json = when {
            trimmed.startsWith("{") -> trimmed
            else -> {
                val code = CODE_REGEX.find(trimmed)?.value
                    ?: throw PlanShareException("没有找到计划分享码")
                inflate(code.removePrefix(PREFIX))
            }
        }
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw PlanShareException("分享码内容已损坏")
        }
        return parse(root)
    }

    private fun inflate(base64: String): String {
        val bytes = try {
            Base64.getUrlDecoder().decode(base64)
        } catch (e: IllegalArgumentException) {
            throw PlanShareException("分享码不完整，请确认复制了全部内容")
        }
        val inflater = Inflater()
        try {
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw PlanShareException("分享码不完整，请确认复制了全部内容")
                }
                out.write(buffer, 0, n)
                if (out.size() > MAX_JSON_BYTES) throw PlanShareException("分享码内容过大")
            }
            return out.toString(Charsets.UTF_8.name())
        } catch (e: java.util.zip.DataFormatException) {
            throw PlanShareException("分享码已损坏")
        } finally {
            inflater.end()
        }
    }

    private fun parse(root: JSONObject): SharedPlan {
        if (root.optString("type") != TYPE) throw PlanShareException("这不是训练计划文件")
        val version = root.optInt("version", 0)
        if (version > VERSION) throw PlanShareException("这个计划来自更新版本的应用，请先升级")

        val name = cleanName(root.optString("name"), "计划名称")
        val daysJson = root.optJSONArray("days") ?: throw PlanShareException("计划里没有训练日")
        if (daysJson.length() == 0) throw PlanShareException("计划里没有训练日")
        if (daysJson.length() > MAX_DAYS) throw PlanShareException("训练日太多（最多 $MAX_DAYS 天）")

        val days = (0 until daysJson.length()).map { i ->
            val d = daysJson.getJSONObject(i)
            val list = d.optJSONArray("exercises") ?: JSONArray()
            if (list.length() > MAX_EXERCISES_PER_DAY) throw PlanShareException("单个训练日的动作太多")
            SharedDay(
                name = cleanName(d.optString("name"), "训练日名称"),
                exercises = (0 until list.length()).map { j ->
                    val e = list.getJSONObject(j)
                    SharedExercise(
                        name = cleanName(e.optString("name"), "动作名称"),
                        group = MuscleGroup.fromName(e.optString("group")),
                        sets = e.optInt("sets", FitnessRepository.DEFAULT_SETS).coerceIn(1, 99),
                        reps = e.optInt("reps", FitnessRepository.DEFAULT_REPS).coerceIn(1, 999),
                        weightKg = if (e.has("weightKg") && !e.isNull("weightKg")) e.getDouble("weightKg").coerceIn(0.0, 1000.0) else null,
                    )
                }.distinctBy { it.name },
            )
        }
        return SharedPlan(name, days)
    }

    private fun cleanName(raw: String, what: String): String {
        val s = raw.trim().replace(Regex("""\s+"""), " ")
        if (s.isEmpty()) throw PlanShareException("$what 为空")
        return s.take(MAX_NAME_LENGTH)
    }

    /** 文件名里不能出现的字符替换掉。 */
    fun fileName(plan: SharedPlan): String =
        "计划_" + plan.name.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").take(40) + ".json"
}
