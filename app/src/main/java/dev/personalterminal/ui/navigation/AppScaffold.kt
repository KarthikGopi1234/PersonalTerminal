package dev.personalterminal.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.prefs.Settings
import dev.personalterminal.ui.boot.BootScreen
import dev.personalterminal.ui.habits.HabitDetailScreen
import dev.personalterminal.ui.habits.HabitEditScreen
import dev.personalterminal.ui.habits.HabitsScreen
import dev.personalterminal.ui.profile.ProfileScreen
import dev.personalterminal.ui.routines.RoutinesScreen
import dev.personalterminal.ui.settings.SettingsScreen
import dev.personalterminal.ui.theme.Term
import dev.personalterminal.ui.timeline.TimelineScreen
import dev.personalterminal.ui.timer.TimerScreen
import dev.personalterminal.ui.today.TodayScreen
import dev.personalterminal.ui.watch.WatchDetailScreen
import dev.personalterminal.ui.watch.WatchEditScreen
import dev.personalterminal.ui.watch.WatchesScreen
import dev.personalterminal.ui.watch.WearLogScreen

@Composable
fun AppScaffold(app: PersonalTerminalApp, settings: Settings, startRoute: String?, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    val p = Term.palette

    if (!settings.onboarded) {
        BootScreen(app = app, onDone = { })
        return
    }

    LaunchedEffect(startRoute) {
        if (startRoute != null) {
            nav.navigate(startRoute) { launchSingleTop = true }
            onRouteConsumed()
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val currentTab = Tab.entries.firstOrNull { t -> currentRoute?.substringBefore("?") == t.route.substringBefore("?") }

    Scaffold(
        containerColor = p.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { StatusLine(nav, currentTab, settings) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = nav, startDestination = Routes.TODAY) {
                composable(Routes.TODAY) { TodayScreen(app, nav) }
                composable(Routes.HABITS) { HabitsScreen(app, nav) }
                composable(
                    Routes.HABIT_EDIT,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("routineId") { type = NavType.LongType; defaultValue = -1L },
                    ),
                ) {
                    val id = it.arguments?.getLong("id") ?: 0L
                    val rid = it.arguments?.getLong("routineId") ?: -1L
                    HabitEditScreen(app, nav, habitId = id, routineId = rid.takeIf { r -> r >= 0 })
                }
                composable(Routes.HABIT_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    HabitDetailScreen(app, nav, it.arguments?.getLong("id") ?: 0L)
                }
                composable(Routes.ROUTINES) { RoutinesScreen(app, nav) }
                composable(Routes.TIMER, arguments = listOf(navArgument("habitId") { type = NavType.LongType; defaultValue = 0L })) {
                    TimerScreen(app, nav, it.arguments?.getLong("habitId") ?: 0L)
                }
                composable(Routes.TIMELINE) { TimelineScreen(app, nav) }
                composable(Routes.WATCHES) { WatchesScreen(app, nav) }
                composable(Routes.WATCH_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })) {
                    WatchEditScreen(app, nav, it.arguments?.getLong("id") ?: 0L)
                }
                composable(Routes.WATCH_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    WatchDetailScreen(app, nav, it.arguments?.getLong("id") ?: 0L)
                }
                composable(Routes.WEAR_LOG, arguments = listOf(navArgument("day") { type = NavType.LongType; defaultValue = -1L })) {
                    WearLogScreen(app, nav, it.arguments?.getLong("day")?.takeIf { d -> d >= 0 })
                }
                composable(Routes.PROFILE) { ProfileScreen(app, nav) }
                composable(Routes.SETTINGS) { SettingsScreen(app, nav) }
            }
        }
    }
}

/** tmux-like status bar: `[0:today] 1:habits 2:timer 3:watch 4:profile        lvl 3 ` */
@Composable
private fun StatusLine(nav: NavHostController, current: Tab?, settings: Settings) {
    val p = Term.palette
    Column(Modifier.fillMaxWidth().background(p.bgAlt)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                val label = if (selected) "[${tab.index}:${tab.label}]" else " ${tab.index}:${tab.label} "
                Text(
                    text = label,
                    color = if (selected) p.green else p.fgDim,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .then(if (selected) Modifier.background(p.bgHighlight) else Modifier)
                        .clickable { nav.switchTab(tab) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * Bottom-bar tab switch.
 *
 * Deliberately *not* the textbook `popUpTo(start) { saveState = true } + restoreState = true`
 * recipe. In this app the start destination (today) is itself a tab, and with that recipe the
 * NavController maps "state saved while popping up to today" onto the today destination. As soon
 * as any screen had been pushed on top of today before a tab switch (habit detail, ⛨ → profile,
 * profile → settings …), tapping `0:today` *restores* that popped screen instead of showing
 * today – every single time. That is the "can't get back to today" bug.
 *
 * So: a tab tap always unwinds to the start destination (dropping detail/edit screens, which is
 * what a tab bar is expected to do) and, for any tab other than today, re-launches the tab root
 * single-top on top of it. Tapping the already-selected tab acts as "back to the tab root".
 */
fun NavHostController.switchTab(tab: Tab) {
    val startId = graph.findStartDestination().id
    if (tab == Tab.TODAY) {
        if (currentDestination?.id == startId) return
        if (!popBackStack(startId, inclusive = false)) navigate(Routes.TODAY) { launchSingleTop = true }
        return
    }
    navigate(tab.route) {
        popUpTo(startId) { inclusive = false; saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}
