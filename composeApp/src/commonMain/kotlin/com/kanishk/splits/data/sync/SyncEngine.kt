package com.kanishk.splits.data.sync

import com.kanishk.splits.data.ExpenseNotice
import com.kanishk.splits.data.NoticeKind
import com.kanishk.splits.data.SplitsRepository
import com.kanishk.splits.data.remote.SplitsApi
import com.kanishk.splits.data.remote.SyncNotConfigured
import com.kanishk.splits.data.showNotification
import com.kanishk.splits.model.formatMinor
import com.kanishk.splits.model.nowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncStatus {
    data object Idle : SyncStatus

    /** No Supabase project wired up — the app is a perfectly good offline app in this state. */
    data object Disabled : SyncStatus

    data object Syncing : SyncStatus
    data class Synced(val at: Long) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

private const val SUMMARY_NOTIFICATION_ID = 1

/**
 * How long a tombstone survives before its storage is reclaimed.
 *
 * This is not arbitrary. A device that has not synced since before a deletion learns about it
 * only from the tombstone: it pulls `updated_at > watermark`, and if the row is simply gone,
 * nothing in the response tells it to drop its local copy. The window has to comfortably
 * exceed the longest realistic gap between one person opening the app and the next.
 */
private const val TOMBSTONE_RETENTION_DAYS = 30
private const val PURGE_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * How long to wait after an edit before uploading it. Long enough that adding three expenses in
 * a row is one round trip rather than three, short enough that it feels immediate.
 */
private const val AUTO_SYNC_DEBOUNCE_MILLIS = 700L

sealed interface JoinResult {
    data class Found(val groupId: String) : JoinResult
    data object NotFound : JoinResult
    data class Failed(val message: String) : JoinResult
}

/**
 * Offline-first, in that order: the local database is always the source of truth for the UI,
 * and sync is a background reconciliation that can fail without the user losing anything.
 *
 * Push always runs before pull. If both sides touched the same row, pushing first means the
 * server has seen our version before we ask what it thinks, so last-write-wins resolves
 * against complete information instead of overwriting a local edit we never sent.
 */
class SyncEngine(
    private val repository: SplitsRepository,
    private val api: SplitsApi = SplitsApi(),
) {
    private val _status = MutableStateFlow<SyncStatus>(
        if (api.isConfigured) SyncStatus.Idle else SyncStatus.Disabled
    )
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    val isEnabled: Boolean get() = api.isConfigured

    // Two pull-to-refresh gestures at once must not produce two interleaved syncs.
    private val gate = Mutex()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Watch for unsent work and upload it on its own. Without this a saved expense sat on
        // the device until the user happened to pull down to refresh, which is not something
        // anyone should have to know to do.
        if (api.isConfigured) {
            scope.launch {
                repository.observeDirtyCount()
                    .distinctUntilChanged()
                    .collectLatest { pending ->
                        if (pending <= 0) return@collectLatest
                        // collectLatest cancels this delay if another edit lands first, so a
                        // burst of changes debounces into a single sync.
                        delay(AUTO_SYNC_DEBOUNCE_MILLIS)
                        syncNow()
                    }
            }
        }
    }

    suspend fun syncNow(): SyncStatus = gate.withLock {
        if (!api.isConfigured) {
            _status.value = SyncStatus.Disabled
            return@withLock SyncStatus.Disabled
        }

        _status.value = SyncStatus.Syncing

        val outcome = runCatching {
            val pending = repository.collectDirty()
            if (!pending.isEmpty) {
                api.push(pending).getOrThrow()
                repository.markPushed(pending)
            }

            val groupIds = repository.knownGroupIds()
            if (groupIds.isNotEmpty()) {
                val since = repository.lastPulledAt()
                val snapshot = api.pull(groupIds, since).getOrThrow()
                if (!snapshot.isEmpty) {
                    val notices = repository.applyRemote(
                        snapshot = snapshot,
                        // Anything we just uploaded is our own work coming back.
                        ignoreExpenseIds = pending.expenses.map { it.id }.toSet(),
                        // Needed to notice groups that have been deleted outright: they are
                        // absent from the response rather than reported in it.
                        requestedGroupIds = groupIds,
                    )
                    val watermark = repository.watermarkOf(snapshot)
                    if (watermark > since) repository.setLastPulledAt(watermark)

                    // A first sync pulls the entire history. Announcing all of it would be
                    // noise, so notifications only start once there is a watermark to be
                    // newer than.
                    if (since > 0 && notices.isNotEmpty() && repository.notificationsEnabled()) {
                        announce(notices)
                    }
                }
            }

            reclaimStorageIfDue()
        }

        val result = outcome.fold(
            onSuccess = { SyncStatus.Synced(nowMillis()) },
            onFailure = { error ->
                if (error is SyncNotConfigured) {
                    SyncStatus.Disabled
                } else {
                    SyncStatus.Failed(error.message ?: "Couldn't reach the server")
                }
            },
        )
        _status.value = result
        result
    }

    /**
     * Hard-deletes aged tombstones on both sides, at most once a day.
     *
     * Runs after the pull rather than before it, so this device has already seen whatever the
     * server was holding. Failures are swallowed: reclaiming disk is housekeeping, and it is
     * not worth reporting a sync as failed over.
     */
    private suspend fun reclaimStorageIfDue() {
        val now = nowMillis()
        if (now - repository.lastPurgeAt() < PURGE_INTERVAL_MILLIS) return

        runCatching {
            api.purgeDeleted(TOMBSTONE_RETENTION_DAYS)
            repository.purgeLocalTombstones(now - TOMBSTONE_RETENTION_DAYS * DAY_MILLIS)
            repository.setLastPurgeAt(now)
        }
    }

    /**
     * Turns what changed into notifications. One change gets the detail; several get a single
     * summary, because a burst of individual notifications for one refresh is unusable.
     */
    private fun announce(notices: List<ExpenseNotice>) {
        if (notices.size == 1) {
            val notice = notices.first()
            val verb = when (notice.kind) {
                NoticeKind.Added -> "added"
                NoticeKind.Updated -> "updated"
                NoticeKind.Removed -> "removed"
            }
            val amount = formatMinor(notice.amountMinor, notice.currencyCode)
            showNotification(
                id = notice.expenseId.hashCode(),
                title = "${'$'}{notice.groupEmoji}  ${'$'}{notice.groupName}",
                body = "${'$'}{notice.actorName} ${'$'}verb \"${'$'}{notice.title}\" · ${'$'}amount",
            )
            return
        }

        val groups = notices.map { it.groupName }.distinct()
        val where = if (groups.size == 1) groups.first() else "${'$'}{groups.size} groups"
        showNotification(
            id = SUMMARY_NOTIFICATION_ID,
            title = "Splits",
            body = "${'$'}{notices.size} updates in ${'$'}where",
        )
    }

    /**
     * Resolves an invite code against the server and writes the group into the local database,
     * so the "who are you?" screen has names to show even on a device that has never seen it.
     */
    suspend fun fetchInvite(inviteCode: String): JoinResult {
        if (!api.isConfigured) return JoinResult.NotFound

        return api.resolveInvite(inviteCode).fold(
            onSuccess = { snapshot ->
                val group = snapshot.groups.firstOrNull { !it.deleted }
                if (!snapshot.found || group == null) {
                    JoinResult.NotFound
                } else {
                    repository.applyRemote(snapshot)
                    JoinResult.Found(group.id)
                }
            },
            onFailure = { error ->
                if (error is SyncNotConfigured) {
                    JoinResult.NotFound
                } else {
                    JoinResult.Failed(error.message ?: "Couldn't reach the server")
                }
            },
        )
    }

    /**
     * Deleting is the one action the server re-checks rather than trusting: the RPC verifies
     * that this device owns the admin member before tombstoning anything.
     */
    suspend fun deleteGroupEverywhere(groupId: String): Boolean {
        if (!api.isConfigured) return true
        return api.deleteGroup(groupId, repository.deviceId)
            .fold(onSuccess = { it.ok }, onFailure = { false })
    }
}
