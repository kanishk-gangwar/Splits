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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.kanishk.splits.data.SplitsRepository
import com.kanishk.splits.data.createSqlDriver
import com.kanishk.splits.data.requestNotificationPermission
import com.kanishk.splits.data.sync.SyncEngine
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

val LocalSyncEngine = staticCompositionLocalOf<SyncEngine> {
    error("No SyncEngine in scope")
}

@Composable
fun App() {
    // One driver, one database, for the life of the process.
    val repository = remember { SplitsRepository(SplitsDatabase(createSqlDriver())) }
    val syncEngine = remember { SyncEngine(repository) }
    val scope = rememberCoroutineScope()
    val themePref by repository.observeThemeMode().collectAsStateWithLifecycle("system")

    LaunchedEffect(Unit) { requestNotificationPermission() }

    // Poll while the app is in the foreground.
    //
    // Without this, a device only ever synced on launch, on pull-to-refresh, or after its own
    // edit — so someone else's expense (and its notification) would not arrive until you
    // happened to reopen or swipe down, even with the app sitting open in front of you.
    //
    // Tied to the resumed state so nothing runs in the background draining battery. This is a
    // foreground poll, not push: a phone with the app closed still hears nothing until it is
    // opened. Real push would need FCM and APNs plus a server holding device tokens.
    LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            while (isActive) {
                // Failing is silent by design: the UI is already rendering local data, and
                // pull-to-refresh remains the explicit retry.
                syncEngine.syncNow()
                delay(FOREGROUND_POLL_MILLIS)
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    CompositionLocalProvider(
        LocalRepository provides repository,
        LocalSyncEngine provides syncEngine,
    ) {
        SplitsTheme(mode = ThemeMode.fromPref(themePref)) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                SplitsNavHost()
            }
        }
    }
}

private const val TRANSITION_MS = 260

/**
 * How often a foregrounded app checks for other people's changes. Frequent enough that a shared
 * expense shows up while you are both looking at the app, infrequent enough to be cheap — each
 * poll is one small request.
 */
private const val FOREGROUND_POLL_MILLIS = 30_000L

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
