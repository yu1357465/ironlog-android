package com.example.fitness_plan.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.fitness_plan.data.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class BodyMetrics(
    val heightCm: Float = 0f,
    val wingspanCm: Float = 0f,
    val bodyWeightKg: Float = 0f,
) {
    /** 臂展 / 身高，缺数据时按 1.0（中性）处理。 */
    val apeIndex: Float
        get() = if (heightCm > 0 && wingspanCm > 0) wingspanCm / heightCm else 1f
}

data class AppSettings(
    val unit: WeightUnit = WeightUnit.KG,
    /** 训练页新增动作时，默认同时写入当前训练日模板。 */
    val addToPlanByDefault: Boolean = true,
    val body: BodyMetrics = BodyMetrics(),
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val unit = stringPreferencesKey("unit")
        val addToPlan = booleanPreferencesKey("add_to_plan")
        val height = floatPreferencesKey("height_cm")
        val wingspan = floatPreferencesKey("wingspan_cm")
        val bodyWeight = floatPreferencesKey("body_weight_kg")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            unit = WeightUnit.entries.firstOrNull { it.name == p[Keys.unit] } ?: WeightUnit.KG,
            addToPlanByDefault = p[Keys.addToPlan] ?: true,
            body = BodyMetrics(
                heightCm = p[Keys.height] ?: 0f,
                wingspanCm = p[Keys.wingspan] ?: 0f,
                bodyWeightKg = p[Keys.bodyWeight] ?: 0f,
            ),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setUnit(unit: WeightUnit) {
        context.dataStore.edit { it[Keys.unit] = unit.name }
    }

    suspend fun setAddToPlanByDefault(value: Boolean) {
        context.dataStore.edit { it[Keys.addToPlan] = value }
    }

    suspend fun setBodyMetrics(body: BodyMetrics) {
        context.dataStore.edit {
            it[Keys.height] = body.heightCm
            it[Keys.wingspan] = body.wingspanCm
            it[Keys.bodyWeight] = body.bodyWeightKg
        }
    }
}
