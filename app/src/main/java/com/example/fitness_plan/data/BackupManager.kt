package com.example.fitness_plan.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import com.example.fitness_plan.data.db.ActiveExerciseEntity
import com.example.fitness_plan.data.db.AppDatabase
import com.example.fitness_plan.data.db.EntryDetail
import com.example.fitness_plan.data.db.ExerciseEntity
import com.example.fitness_plan.data.db.PlanDayEntity
import com.example.fitness_plan.data.db.PlanDayExerciseEntity
import com.example.fitness_plan.data.db.PlanEntity
import com.example.fitness_plan.data.db.WorkoutEntryEntity
import com.example.fitness_plan.data.db.WorkoutSessionEntity
import com.example.fitness_plan.data.model.MuscleGroup
import com.example.fitness_plan.data.model.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 完整备份（JSON，含动作库/计划/今日训练/历史，以及身体数据和偏好设置）与 CSV 报表导出。
 * 文件写到 下载/FitnessPlan 目录，无需存储权限。
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {

    private val dao = db.dao()

    suspend fun exportBackup(): Uri = withContext(Dispatchers.IO) {
        val json = buildBackupJson().toString(2)
        writeToDownloads("FitnessPlan_${timestamp()}.json", "application/json", json.toByteArray())
    }

    suspend fun exportCsv(): Uri = withContext(Dispatchers.IO) {
        val csv = Csv.historyReport(dao.allEntryDetails(), dao.allSessions().associateBy { it.id })
        // BOM：避免 Excel 打开中文乱码
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + csv.toByteArray()
        writeToDownloads("FitnessPlan_${timestamp()}.csv", "text/csv", bytes)
    }

    /** 导入模板：带示例的 CSV，与训练报表同格式。 */
    suspend fun exportImportTemplate(): Uri = withContext(Dispatchers.IO) {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + HistoryCsv.template().toByteArray()
        writeToDownloads("训练记录导入模板.csv", "text/csv", bytes)
    }

    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let(::decode) ?: error("无法读取文件")
    }

    /** 优先按 UTF-8 解码；Excel 另存的中文 CSV 常是 GBK，UTF-8 解出乱码时改用 GBK。 */
    private fun decode(bytes: ByteArray): String {
        val utf8 = bytes.decodeToString()
        return if ('�' in utf8) String(bytes, charset("GBK")) else utf8
    }

    /** 用备份文件整体替换当前数据。文件不合法时抛异常且不改动任何数据。 */
    suspend fun restore(uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("无法读取文件")
        val root = JSONObject(text)
        val version = root.optInt("version", 0)
        require(version == BACKUP_VERSION) {
            if (version < BACKUP_VERSION) "这是旧版应用的备份（缺少动作库），无法导入" else "备份版本过新，请先升级应用"
        }
        val exercises = root.getJSONArray("exercises").map {
            ExerciseEntity(it.getLong("id"), it.getString("name"), MuscleGroup.fromName(it.getString("muscleGroup")), it.getBoolean("archived"), it.getLong("createdAt"))
        }
        val plans = root.getJSONArray("plans").map {
            PlanEntity(it.getLong("id"), it.getString("name"), it.getInt("sortOrder"), it.getBoolean("isActive"), it.optLongOrNull("currentDayId"))
        }
        val days = root.getJSONArray("days").map {
            PlanDayEntity(it.getLong("id"), it.getLong("planId"), it.getString("name"), it.getInt("sortOrder"))
        }
        val dayExercises = root.getJSONArray("dayExercises").map {
            PlanDayExerciseEntity(it.getLong("id"), it.getLong("dayId"), it.getLong("exerciseId"), it.getInt("sets"), it.getInt("reps"), it.getDouble("weightKg"), it.getInt("sortOrder"))
        }
        val active = root.getJSONArray("active").map {
            ActiveExerciseEntity(it.getLong("id"), it.getLong("exerciseId"), it.getDouble("weightKg"), it.getInt("sets"), it.getInt("reps"), it.getBoolean("completed"), it.getBoolean("inPlan"), it.getInt("sortOrder"))
        }
        val sessions = root.getJSONArray("sessions").map {
            WorkoutSessionEntity(it.getLong("id"), it.getLong("performedAt"), it.optStringOrNull("planName"), it.optStringOrNull("dayName"))
        }
        val entries = root.getJSONArray("entries").map {
            WorkoutEntryEntity(it.getLong("id"), it.getLong("sessionId"), it.getLong("exerciseId"), it.getDouble("weightKg"), it.getInt("sets"), it.getInt("reps"), it.getInt("sortOrder"))
        }
        // 设置是后来加入的可选字段：较早的备份没有它，恢复时保留当前设置不变
        val restoredSettings = root.optJSONObject("settings")?.let { s ->
            val body = s.optJSONObject("body")
            AppSettings(
                unit = WeightUnit.entries.firstOrNull { it.name == s.optString("unit") } ?: WeightUnit.KG,
                addToPlanByDefault = s.optBoolean("addToPlanByDefault", true),
                body = BodyMetrics(
                    heightCm = body?.optDouble("heightCm", 0.0)?.toFloat() ?: 0f,
                    wingspanCm = body?.optDouble("wingspanCm", 0.0)?.toFloat() ?: 0f,
                    bodyWeightKg = body?.optDouble("bodyWeightKg", 0.0)?.toFloat() ?: 0f,
                ),
            )
        }

        db.withTransaction {
            db.clearAllTables()
            dao.insertExercises(exercises)
            dao.insertPlans(plans)
            dao.insertDays(days)
            dao.insertDayExercises(dayExercises)
            dao.insertActives(active)
            dao.insertSessions(sessions)
            dao.insertEntries(entries)
        }
        restoredSettings?.let { settings.replaceAll(it) }
    }

    private suspend fun buildBackupJson(): JSONObject = JSONObject().apply {
        put("version", BACKUP_VERSION)
        put("exportedAt", System.currentTimeMillis())
        val s = settings.current()
        put("settings", JSONObject().apply {
            put("unit", s.unit.name)
            put("addToPlanByDefault", s.addToPlanByDefault)
            put("body", JSONObject().apply {
                put("heightCm", s.body.heightCm.toDouble())
                put("wingspanCm", s.body.wingspanCm.toDouble())
                put("bodyWeightKg", s.body.bodyWeightKg.toDouble())
            })
        })
        put("exercises", dao.allExercises().toJsonArray {
            put("id", it.id); put("name", it.name); put("muscleGroup", it.muscleGroup.name)
            put("archived", it.archived); put("createdAt", it.createdAt)
        })
        put("plans", dao.allPlans().toJsonArray {
            put("id", it.id); put("name", it.name); put("sortOrder", it.sortOrder)
            put("isActive", it.isActive); put("currentDayId", it.currentDayId ?: JSONObject.NULL)
        })
        put("days", dao.allDays().toJsonArray {
            put("id", it.id); put("planId", it.planId); put("name", it.name); put("sortOrder", it.sortOrder)
        })
        put("dayExercises", dao.allDayExercises().toJsonArray {
            put("id", it.id); put("dayId", it.dayId); put("exerciseId", it.exerciseId)
            put("sets", it.sets); put("reps", it.reps); put("weightKg", it.weightKg); put("sortOrder", it.sortOrder)
        })
        put("active", dao.allActive().toJsonArray {
            put("id", it.id); put("exerciseId", it.exerciseId); put("weightKg", it.weightKg)
            put("sets", it.sets); put("reps", it.reps); put("completed", it.completed)
            put("inPlan", it.inPlan); put("sortOrder", it.sortOrder)
        })
        put("sessions", dao.allSessions().toJsonArray {
            put("id", it.id); put("performedAt", it.performedAt)
            put("planName", it.planName ?: JSONObject.NULL); put("dayName", it.dayName ?: JSONObject.NULL)
        })
        put("entries", dao.allEntries().toJsonArray {
            put("id", it.id); put("sessionId", it.sessionId); put("exerciseId", it.exerciseId)
            put("weightKg", it.weightKg); put("sets", it.sets); put("reps", it.reps); put("sortOrder", it.sortOrder)
        })
    }

    private fun writeToDownloads(fileName: String, mimeType: String, bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("无法创建文件")
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入文件")
        return uri
    }

    private fun timestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    companion object {
        const val BACKUP_VERSION = 2
        const val FOLDER = "FitnessPlan"
    }
}

object Csv {
    fun historyReport(entries: List<EntryDetail>, sessions: Map<Long, WorkoutSessionEntity>): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return buildString {
            append(HistoryCsv.HEADER).append('\n')
            entries.forEach { d ->
                val session = sessions[d.entry.sessionId]
                append(
                    listOf(
                        sdf.format(Date(d.performedAt)),
                        session?.planName.orEmpty(),
                        session?.dayName.orEmpty(),
                        d.name,
                        d.muscleGroup.label,
                        d.entry.weightKg.toString(),
                        d.entry.sets.toString(),
                        d.entry.reps.toString(),
                    ).joinToString(",") { escape(it) }
                )
                append('\n')
            }
        }
    }

    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\""
        else value
}

private inline fun <T> List<T>.toJsonArray(block: JSONObject.(T) -> Unit): JSONArray =
    JSONArray().also { array -> forEach { array.put(JSONObject().apply { block(it) }) } }

private inline fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }

private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
