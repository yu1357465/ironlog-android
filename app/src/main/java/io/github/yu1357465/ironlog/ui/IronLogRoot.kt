package io.github.yu1357465.ironlog.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.yu1357465.ironlog.ui.theme.AppIcons
import io.github.yu1357465.ironlog.ui.components.LocalSnackbar
import io.github.yu1357465.ironlog.ui.history.HistoryScreen
import io.github.yu1357465.ironlog.ui.library.LibraryScreen
import io.github.yu1357465.ironlog.ui.plans.PlanDayScreen
import io.github.yu1357465.ironlog.ui.plans.PlansScreen
import io.github.yu1357465.ironlog.ui.settings.SettingsScreen
import io.github.yu1357465.ironlog.ui.stats.StatsScreen
import io.github.yu1357465.ironlog.ui.workout.WorkoutScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    WORKOUT("workout", "训练", AppIcons.MarkOutlined, AppIcons.Mark),
    HISTORY("history", "历史", Icons.Outlined.History, Icons.Filled.History),
    STATS("stats", "分析", Icons.Outlined.BarChart, Icons.Filled.BarChart),
    ME("me", "我的", Icons.Outlined.Person, Icons.Filled.Person),
}

private object Routes {
    const val PLANS = "plans"
    const val LIBRARY = "library"
    const val DAY = "day/{dayId}"
    fun day(id: Long) = "day/$id"
}

@Composable
fun IronLogRoot() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == route }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets = WindowInsets(0),
            bottomBar = { if (showBar) BottomBar(nav, route) },
        ) { padding ->
            // 从文件选择器等其他界面返回时，窗口重新获得焦点，Compose 会把焦点自动移入
            // 页面左上角最近的可聚焦元素；页面里有输入框时就会弹出键盘。
            // 放一个不可见的焦点锚点在最左上角接住这次自动聚焦。点击输入框不受影响。
            Box(
                Modifier
                    .size(1.dp)
                    .clearAndSetSemantics {}
                    .focusable(),
            )
            NavHost(
                navController = nav,
                startDestination = Tab.WORKOUT.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                // 默认是 700ms 交叉淡入淡出，两个页面同时绘制，切换时明显拖沓
                enterTransition = {
                    if (isTabSwitch()) fadeIn(tween(TAB_FADE_MS))
                    else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(PUSH_MS)) + fadeIn(tween(PUSH_MS))
                },
                exitTransition = {
                    if (isTabSwitch()) fadeOut(tween(TAB_FADE_MS / 2))
                    else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(PUSH_MS), targetOffset = { it / 4 }) + fadeOut(tween(PUSH_MS))
                },
                popEnterTransition = {
                    if (isTabSwitch()) fadeIn(tween(TAB_FADE_MS))
                    else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(PUSH_MS), initialOffset = { it / 4 }) + fadeIn(tween(PUSH_MS))
                },
                popExitTransition = {
                    if (isTabSwitch()) fadeOut(tween(TAB_FADE_MS / 2))
                    else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(PUSH_MS)) + fadeOut(tween(PUSH_MS))
                },
            ) {
                composable(Tab.WORKOUT.route) {
                    WorkoutScreen(
                        onOpenPlans = { nav.navigate(Routes.PLANS) },
                        onOpenSettings = { nav.switchTab(Tab.ME) },
                    )
                }
                composable(Tab.HISTORY.route) { HistoryScreen() }
                composable(Tab.STATS.route) { StatsScreen(onOpenSettings = { nav.switchTab(Tab.ME) }) }
                composable(Tab.ME.route) {
                    SettingsScreen(
                        onOpenPlans = { nav.navigate(Routes.PLANS) },
                        onOpenLibrary = { nav.navigate(Routes.LIBRARY) },
                    )
                }
                composable(Routes.PLANS) {
                    PlansScreen(onBack = { nav.popBackStack() }, onOpenDay = { nav.navigate(Routes.day(it)) })
                }
                composable(Routes.DAY, arguments = listOf(navArgument("dayId") { type = NavType.LongType })) { entry ->
                    PlanDayScreen(dayId = entry.arguments!!.getLong("dayId"), onBack = { nav.popBackStack() })
                }
                composable(Routes.LIBRARY) { LibraryScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}

private const val TAB_FADE_MS = 150
private const val PUSH_MS = 260

/** 两个底部标签之间切换只做淡入淡出；进入/返回二级页面用横向滑动。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val tabs = Tab.entries.map { it.route }
    return initialState.destination.route in tabs && targetState.destination.route in tabs
}

private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
        Tab.entries.forEach { tab ->
            val selected = route == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) nav.switchTab(tab) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}
