package com.example.fitness_plan

import android.app.Application
import com.example.fitness_plan.data.BackupManager
import com.example.fitness_plan.data.FitnessRepository
import com.example.fitness_plan.data.SettingsRepository
import com.example.fitness_plan.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 手动依赖注入：全局唯一的数据库与仓库。 */
class AppContainer(app: Application) {
    private val database = AppDatabase.build(app)
    val repository = FitnessRepository(database)
    val settings = SettingsRepository(app)
    val backup = BackupManager(app, database)
}

class FitnessApp : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch { container.repository.seedIfEmpty() }
    }
}
