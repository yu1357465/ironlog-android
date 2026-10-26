package io.github.yu1357465.ironlog

import android.app.Application
import io.github.yu1357465.ironlog.data.BackupManager
import io.github.yu1357465.ironlog.data.FitnessRepository
import io.github.yu1357465.ironlog.data.SettingsRepository
import io.github.yu1357465.ironlog.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 手动依赖注入：全局唯一的数据库与仓库。 */
class AppContainer(app: Application) {
    private val database = AppDatabase.build(app)
    val repository = FitnessRepository(database)
    val settings = SettingsRepository(app)
    val backup = BackupManager(app, database, settings)
}

class IronLogApp : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch { container.repository.seedIfEmpty() }
    }
}
