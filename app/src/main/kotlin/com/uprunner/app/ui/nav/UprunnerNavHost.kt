package com.uprunner.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.uprunner.app.ui.activerun.ActiveRunScreen
import com.uprunner.app.ui.chat.ChatScreen
import com.uprunner.app.ui.plan.PlanScreen
import com.uprunner.app.ui.stats.StatsScreen

private sealed class UprunnerTab(val route: String, val labelResId: String) {
    data object ActiveRun : UprunnerTab("active_run", "Run")
    data object Plan : UprunnerTab("plan", "Plan")
    data object Chat : UprunnerTab("chat", "Chat")
    data object Stats : UprunnerTab("stats", "Stats")
}

private val tabs = listOf(UprunnerTab.ActiveRun, UprunnerTab.Plan, UprunnerTab.Chat, UprunnerTab.Stats)

@Composable
fun UprunnerNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(iconFor(tab), contentDescription = tab.labelResId) },
                        label = { Text(tab.labelResId) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = UprunnerTab.ActiveRun.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(UprunnerTab.ActiveRun.route) { ActiveRunScreen() }
            composable(UprunnerTab.Plan.route) { PlanScreen() }
            composable(UprunnerTab.Chat.route) { ChatScreen() }
            composable(UprunnerTab.Stats.route) { StatsScreen() }
        }
    }
}

private fun iconFor(tab: UprunnerTab) = when (tab) {
    UprunnerTab.ActiveRun -> Icons.Filled.DirectionsRun
    UprunnerTab.Plan -> Icons.Filled.Map
    UprunnerTab.Chat -> Icons.Filled.Chat
    UprunnerTab.Stats -> Icons.Filled.QueryStats
}
