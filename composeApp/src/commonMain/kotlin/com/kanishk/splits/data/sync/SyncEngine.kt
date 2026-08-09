package com.kanishk.splits.data.sync

import com.kanishk.splits.data.SplitsRepository
import com.kanishk.splits.data.remote.SplitsApi
import com.kanishk.splits.data.remote.SyncNotConfigured
import com.kanishk.splits.model.nowMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                    repository.applyRemote(snapshot)
                    val watermark = repository.watermarkOf(snapshot)
                    if (watermark > since) repository.setLastPulledAt(watermark)
                }
            }
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
