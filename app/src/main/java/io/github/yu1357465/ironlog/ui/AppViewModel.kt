package io.github.yu1357465.ironlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yu1357465.ironlog.AppContainer
import io.github.yu1357465.ironlog.IronLogApp

/** 用全局 [AppContainer] 构造 ViewModel。 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as IronLogApp
    return viewModel(key = key, factory = viewModelFactory { initializer { create(app.container) } })
}

/** 一次性提示（Snackbar），可附带撤销操作。 */
data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)
