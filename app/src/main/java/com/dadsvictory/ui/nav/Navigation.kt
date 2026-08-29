package com.dadsvictory.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.VictoryViewModel
import com.dadsvictory.ui.achievements.AchievementsScreen
import com.dadsvictory.ui.checkin.CheckInScreen
import com.dadsvictory.ui.craving.CravingScreen
import com.dadsvictory.ui.facts.FactsScreen
import com.dadsvictory.ui.facts.SourcesScreen
import com.dadsvictory.ui.faith.FaithScreen
import com.dadsvictory.ui.family.FamilyScreen
import com.dadsvictory.ui.help.HelpScreen
import com.dadsvictory.ui.home.HomeScreen
import com.dadsvictory.ui.journal.JournalScreen
import com.dadsvictory.ui.money.MoneyScreen
import com.dadsvictory.ui.onboarding.OnboardingFlow
import com.dadsvictory.ui.plan.PlanScreen
import com.dadsvictory.ui.progress.ProgressScreen
import com.dadsvictory.ui.relapse.RelapseScreen
import com.dadsvictory.ui.settings.SettingsScreen
import com.dadsvictory.ui.triggers.TriggersScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PROGRESS = "progress"
    const val CRAVING = "craving"
    const val FAITH = "faith"
    const val SETTINGS = "settings"

    const val JOURNAL = "journal"
    const val PLAN = "plan"
    const val FACTS = "facts"
    const val SOURCES = "sources"
    const val TRIGGERS = "triggers"
    const val ACHIEVEMENTS = "achievements"
    const val CHECK_IN = "checkin"
    const val RELAPSE = "relapse"
    const val FAMILY = "family"
    const val HELP = "help"
    const val MONEY = "money"
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.PROGRESS, "Progress", Icons.AutoMirrored.Filled.TrendingUp),
    Tab(Routes.CRAVING, "Cravings", Icons.Filled.LocalFireDepartment),
    Tab(Routes.FAITH, "Faith", Icons.AutoMirrored.Filled.MenuBook),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun VictoryApp(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
    openCravingRequest: Boolean,
    onCravingRequestHandled: () -> Unit,
) {
    val navController = rememberNavController()

    if (!state.settings.onboardingComplete) {
        OnboardingFlow(
            onFinished = { profile, drinkingDays ->
                viewModel.completeOnboarding(profile, drinkingDays)
            },
        )
        return
    }

    // Alarms are cleared by a reboot or a force-stop, so re-arm on every launch.
    LaunchedEffect(state.settings.onboardingComplete) {
        viewModel.rescheduleNotifications()
        viewModel.seedPlanIfNeeded()
    }

    // Opened from the afternoon notification's "I'M HAVING A CRAVING" button.
    LaunchedEffect(openCravingRequest) {
        if (openCravingRequest) {
            navController.navigate(Routes.CRAVING) { launchSingleTop = true }
            onCravingRequestHandled()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TABS.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val destination = backStackEntry?.destination
                    for (tab in TABS) {
                        val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(viewModel, state, navController)
            }
            composable(Routes.PROGRESS) {
                ProgressScreen(viewModel, state, navController)
            }
            composable(Routes.CRAVING) {
                CravingScreen(viewModel, state, navController)
            }
            composable(Routes.FAITH) {
                FaithScreen(viewModel, state)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel, state, navController)
            }
            composable(Routes.JOURNAL) {
                JournalScreen(viewModel, state, navController)
            }
            composable(Routes.PLAN) {
                PlanScreen(viewModel, state, navController)
            }
            composable(Routes.FACTS) {
                FactsScreen(state, navController)
            }
            composable(Routes.SOURCES) {
                SourcesScreen(navController)
            }
            composable(Routes.TRIGGERS) {
                TriggersScreen(viewModel, state, navController)
            }
            composable(Routes.ACHIEVEMENTS) {
                AchievementsScreen(viewModel, state, navController)
            }
            composable(Routes.CHECK_IN) {
                CheckInScreen(viewModel, state, navController)
            }
            composable(Routes.RELAPSE) {
                RelapseScreen(viewModel, state, navController)
            }
            composable(Routes.FAMILY) {
                FamilyScreen(viewModel, state, navController)
            }
            composable(Routes.HELP) {
                HelpScreen(state, navController)
            }
            composable(Routes.MONEY) {
                MoneyScreen(viewModel, state, navController)
            }
        }
    }
}

/** Standard bottom-nav behaviour: one entry per tab, state preserved. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
