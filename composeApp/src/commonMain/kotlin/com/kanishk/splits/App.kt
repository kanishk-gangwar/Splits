package com.kanishk.splits

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanishk.splits.data.SplitsRepository
import com.kanishk.splits.data.createSqlDriver
import com.kanishk.splits.db.SplitsDatabase
import com.kanishk.splits.ui.CreateGroupRoute
import com.kanishk.splits.ui.ExpenseEditorRoute
import com.kanishk.splits.ui.GroupRoute
import com.kanishk.splits.ui.GroupSettingsRoute
import com.kanishk.splits.ui.GroupsRoute
import com.kanishk.splits.ui.JoinRoute
import com.kanishk.splits.ui.SettingsRoute
import com.kanishk.splits.ui.expense.ExpenseEditorScreen
import com.kanishk.splits.ui.group.GroupScreen
import com.kanishk.splits.ui.group.GroupSettingsScreen
import com.kanishk.splits.ui.groups.CreateGroupScreen
import com.kanishk.splits.ui.groups.GroupsScreen
import com.kanishk.splits.ui.join.JoinScreen
import com.kanishk.splits.ui.settings.SettingsScreen
import com.kanishk.splits.ui.theme.SplitsTheme
import com.kanishk.splits.ui.theme.ThemeMode

val LocalRepository = staticCompositionLocalOf<SplitsRepository> {
    error("No SplitsRepository in scope")
}

@Composable
fun App() {
    // One driver, one database, for the life of the process.
    val repository = remember { SplitsRepository(SplitsDatabase(createSqlDriver())) }
    val themePref by repository.observeThemeMode().collectAsStateWithLifecycle("system")

    CompositionLocalProvider(LocalRepository provides repository) {
        SplitsTheme(mode = ThemeMode.fromPref(themePref)) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                SplitsNavHost()
            }
        }
    }
}

private const val TRANSITION_MS = 260

@Composable
private fun SplitsNavHost() {
    val navController = rememberNavController()
    HandlePendingInvites(navController)

    NavHost(
        navController = navController,
        startDestination = GroupsRoute,
        enterTransition = {
            slideInHorizontally(tween(TRANSITION_MS)) { it / 6 } + fadeIn(tween(TRANSITION_MS))
        },
        exitTransition = {
            slideOutHorizontally(tween(TRANSITION_MS)) { -it / 8 } + fadeOut(tween(TRANSITION_MS))
        },
        popEnterTransition = {
            slideInHorizontally(tween(TRANSITION_MS)) { -it / 8 } + fadeIn(tween(TRANSITION_MS))
        },
        popExitTransition = {
            slideOutHorizontally(tween(TRANSITION_MS)) { it / 6 } + fadeOut(tween(TRANSITION_MS))
        },
    ) {
        composable<GroupsRoute> {
            GroupsScreen(
                onCreateGroup = { navController.navigate(CreateGroupRoute) },
                onOpenGroup = { groupId -> navController.navigate(GroupRoute(groupId)) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onJoinWithCode = { code -> navController.navigate(JoinRoute(code)) },
            )
        }

        composable<CreateGroupRoute> {
            CreateGroupScreen(
                onDone = { groupId ->
                    navController.popBackStack()
                    navController.navigate(GroupRoute(groupId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<GroupRoute> { entry ->
            val route = entry.toRoute<GroupRoute>()
            GroupScreen(
                groupId = route.groupId,
                onBack = { navController.popBackStack() },
                onAddExpense = {
                    navController.navigate(ExpenseEditorRoute(groupId = route.groupId))
                },
                onEditExpense = { expenseId ->
                    navController.navigate(
                        ExpenseEditorRoute(groupId = route.groupId, expenseId = expenseId)
                    )
                },
                onSettleUp = { fromId, toId, amountMinor ->
                    navController.navigate(
                        ExpenseEditorRoute(
                            groupId = route.groupId,
                            presetKind = "REIMBURSEMENT",
                            presetFromMemberId = fromId,
                            presetToMemberId = toId,
                            presetAmountMinor = amountMinor,
                        )
                    )
                },
                onOpenGroupSettings = {
                    navController.navigate(GroupSettingsRoute(route.groupId))
                },
                onGroupGone = {
                    navController.popBackStack(GroupsRoute, inclusive = false)
                },
            )
        }

        composable<GroupSettingsRoute> { entry ->
            val route = entry.toRoute<GroupSettingsRoute>()
            GroupSettingsScreen(
                groupId = route.groupId,
                onBack = { navController.popBackStack() },
                onLeftGroupList = {
                    navController.popBackStack(GroupsRoute, inclusive = false)
                },
            )
        }

        composable<ExpenseEditorRoute> { entry ->
            val route = entry.toRoute<ExpenseEditorRoute>()
            ExpenseEditorScreen(
                route = route,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable<JoinRoute> { entry ->
            val route = entry.toRoute<JoinRoute>()
            JoinScreen(
                inviteCode = route.inviteCode,
                onJoined = { groupId ->
                    navController.popBackStack()
                    navController.navigate(GroupRoute(groupId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** A link tapped outside the app drops the user straight onto the "who are you?" screen. */
@Composable
private fun HandlePendingInvites(navController: NavHostController) {
    val pending by DeepLinks.pendingInvite.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val code = pending ?: return@LaunchedEffect
        DeepLinks.consume()
        navController.navigate(JoinRoute(code))
    }
}
