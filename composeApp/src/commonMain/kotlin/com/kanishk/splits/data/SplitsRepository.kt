package com.kanishk.splits.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.kanishk.splits.db.ExpenseEntity
import com.kanishk.splits.db.GroupEntity
import com.kanishk.splits.db.MemberEntity
import com.kanishk.splits.db.SplitEntity
import com.kanishk.splits.data.remote.PushPayload
import com.kanishk.splits.data.remote.RemoteExpense
import com.kanishk.splits.data.remote.RemoteGroup
import com.kanishk.splits.data.remote.RemoteMember
import com.kanishk.splits.data.remote.RemoteShare
import com.kanishk.splits.data.remote.RemoteSnapshot
import com.kanishk.splits.db.SplitsDatabase
import com.kanishk.splits.model.Expense
import com.kanishk.splits.model.ExpenseKind
import com.kanishk.splits.model.Group
import com.kanishk.splits.model.GroupCard
import com.kanishk.splits.model.GroupDetail
import com.kanishk.splits.model.Member
import com.kanishk.splits.model.OverallPosition
import com.kanishk.splits.model.Split
import com.kanishk.splits.model.nowMillis
import com.kanishk.splits.model.summarise
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val PREF_DEVICE_ID = "device_id"
private const val PREF_DISPLAY_NAME = "display_name"
private const val PREF_THEME = "theme_mode"
private const val PREF_LAST_PULL = "last_pulled_at"
private const val PREF_NOTIFICATIONS = "notifications_enabled"
private const val PREF_LAST_PURGE = "last_purge_at"

class SplitsRepository(
    private val database: SplitsDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val queries = database.splitsQueries

    /**
     * The id this install would have if it were derived from the platform's stable install id
     * (see [stableInstallId]), or null where the platform offers none.
     */
    private val stableDeviceId: String? = deriveDeviceId(stableInstallId())

    @Volatile
    private var currentDeviceId: String = run {
        val stored = queries.selectPref(PREF_DEVICE_ID).executeAsOneOrNull()
        // A stored id is kept even when it differs from the stable one: it is what the server
        // knows this install as, and switching underneath it would orphan every claim. The
        // switch happens through [completeDeviceRotation] once the server has moved them.
        stored ?: (stableDeviceId ?: newId()).also { queries.upsertPref(PREF_DEVICE_ID, it) }
    }

    /**
     * Identity in this app is the device, not an account — participants prove who they are by
     * picking their name off the invite screen.
     *
     * The id is derived from the platform's stable install id where there is one, which is
     * what lets a reinstalled app find its names again: before this it was random and kept in
     * this database, so an uninstall took it — and with it every claimed name — along. Installs
     * that minted a random id under the old scheme are moved over once, by
     * [pendingDeviceRotation] and [completeDeviceRotation].
     */
    val deviceId: String get() = currentDeviceId

    /**
     * `(old, new)` while this install still signs as an id minted before ids became stable, or
     * null once it is on the stable id (or the platform has none). `SyncEngine` asks the server
     * to move the claims first, then calls [completeDeviceRotation].
     */
    val pendingDeviceRotation: Pair<String, String>?
        get() {
            val target = stableDeviceId ?: return null
            val current = currentDeviceId
            return if (current == target) null else current to target
        }

    /**
     * Switches this install to [newDeviceId]: every claim held locally is re-pointed and the
     * id is persisted, so the next pull and push use it. Called only after the server has
     * done the same, or when there is no server to tell.
     */
    suspend fun completeDeviceRotation(newDeviceId: String) = withContext(dispatcher) {
        val old = currentDeviceId
        if (old == newDeviceId) return@withContext
        database.transaction {
            queries.rotateClaims(newDeviceId, old)
            queries.upsertPref(PREF_DEVICE_ID, newDeviceId)
        }
        currentDeviceId = newDeviceId
    }

    // ------------------------------------------------------------------ prefs --

    fun observeDisplayName(): Flow<String> =
        queries.selectPref(PREF_DISPLAY_NAME).asFlow().mapToOneOrNull(dispatcher).map { it.orEmpty() }

    suspend fun setDisplayName(name: String) = withContext(dispatcher) {
        queries.upsertPref(PREF_DISPLAY_NAME, name.trim())
    }

    fun observeNotificationsEnabled(): Flow<Boolean> =
        queries.selectPref(PREF_NOTIFICATIONS).asFlow().mapToOneOrNull(dispatcher)
            .map { it != "false" }

    suspend fun notificationsEnabled(): Boolean = withContext(dispatcher) {
        queries.selectPref(PREF_NOTIFICATIONS).executeAsOneOrNull() != "false"
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) = withContext(dispatcher) {
        queries.upsertPref(PREF_NOTIFICATIONS, enabled.toString())
    }

    fun observeThemeMode(): Flow<String> =
        queries.selectPref(PREF_THEME).asFlow().mapToOneOrNull(dispatcher).map { it ?: "system" }

    suspend fun setThemeMode(mode: String) = withContext(dispatcher) {
        queries.upsertPref(PREF_THEME, mode)
    }

    // ----------------------------------------------------------------- reading --

    /**
     * The home screen needs balances for every group at once. The data set here is small
     * (a handful of groups, a few hundred expenses), so loading it whole and folding it in
     * memory is both simpler and faster than a query per card.
     */
    fun observeGroupCards(): Flow<List<GroupCard>> {
        val groups = queries.selectVisibleGroups().asFlow().mapToList(dispatcher)
        val members = queries.selectAllMembers().asFlow().mapToList(dispatcher)
        val expenses = queries.selectAllExpenses().asFlow().mapToList(dispatcher)
        val splits = queries.selectAllSplits().asFlow().mapToList(dispatcher)

        return combine(groups, members, expenses, splits) { g, m, e, s ->
            buildCards(g, m, e, s)
        }
    }

    fun observeHiddenGroups(): Flow<List<Group>> =
        queries.selectHiddenGroups().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toModel() } }

    fun observeGroupDetail(groupId: String): Flow<GroupDetail?> {
        val group = queries.selectGroupById(groupId).asFlow().mapToOneOrNull(dispatcher)
        val members = queries.selectMembersOfGroup(groupId).asFlow().mapToList(dispatcher)
        val expenses = queries.selectExpensesOfGroup(groupId).asFlow().mapToList(dispatcher)
        val splits = queries.selectSplitsOfGroup(groupId).asFlow().mapToList(dispatcher)

        return combine(group, members, expenses, splits) { g, m, e, s ->
            if (g == null) return@combine null
            val bySplit = s.groupBy { it.expenseId }
            GroupDetail(
                group = g.toModel(),
                members = m.map { it.toModel() },
                expenses = e.map { row -> row.toModel(bySplit[row.id].orEmpty()) },
            )
        }
    }

    suspend fun findGroupByInvite(inviteCode: String): Group? = withContext(dispatcher) {
        queries.selectGroupByInvite(normaliseInviteCode(inviteCode)).executeAsOneOrNull()?.toModel()
    }

    suspend fun loadExpense(expenseId: String): Expense? = withContext(dispatcher) {
        val row = queries.selectExpenseById(expenseId).executeAsOneOrNull() ?: return@withContext null
        row.toModel(queries.selectSplitsOfExpense(expenseId).executeAsList())
    }

    // ----------------------------------------------------------------- writing --

    /**
     * Creates the group with [myName] as the first participant. That member is claimed by this
     * device and made admin, which is what gates the delete-group action later on.
     */
    suspend fun createGroup(
        name: String,
        emoji: String,
        currencyCode: String,
        myName: String,
        otherNames: List<String>,
    ): String = withContext(dispatcher) {
        val now = nowMillis()
        val groupId = newId()
        val myMemberId = newId()

        database.transaction {
            queries.upsertGroup(
                id = groupId,
                name = name.trim(),
                emoji = emoji,
                currencyCode = currencyCode,
                inviteCode = newInviteCode(),
                adminMemberId = myMemberId,
                createdAt = now,
                updatedAt = now,
                archived = false,
                hidden = false,
                deleted = false,
                dirty = true,
                simplifyDebts = true,
            )

            queries.upsertMember(
                id = myMemberId,
                groupId = groupId,
                name = myName.trim(),
                colorIndex = 0L,
                claimedByDeviceId = deviceId,
                createdAt = now,
                updatedAt = now,
                deleted = false,
                dirty = true,
            )

            otherNames.filter { it.isNotBlank() }.forEachIndexed { index, participant ->
                queries.upsertMember(
                    id = newId(),
                    groupId = groupId,
                    name = participant.trim(),
                    colorIndex = (index + 1).toLong(),
                    claimedByDeviceId = null,
                    createdAt = now + index + 1,
                    updatedAt = now,
                    deleted = false,
                    dirty = true,
                )
            }

            queries.upsertPref(PREF_DISPLAY_NAME, myName.trim())
        }

        groupId
    }

    suspend fun updateGroup(groupId: String, name: String, emoji: String, currencyCode: String) =
        withContext(dispatcher) {
            queries.renameGroup(name.trim(), emoji, currencyCode, nowMillis(), groupId)
        }

    /**
     * Fewest payments or every pairwise debt on the settle-up tab — see `Group.simplifyDebts`.
     * Admin-only: `GroupDetail.canChangeSettleMode` decides whether the switch is offered and
     * `splits_push` refuses the change from any other device.
     */
    suspend fun setSimplifyDebts(groupId: String, simplify: Boolean) = withContext(dispatcher) {
        queries.setGroupSimplifyDebts(simplify, nowMillis(), groupId)
    }

    suspend fun addMember(groupId: String, name: String): String = withContext(dispatcher) {
        val id = newId()
        val now = nowMillis()
        val existing = queries.selectMembersOfGroup(groupId).executeAsList().size
        queries.upsertMember(
            id = id,
            groupId = groupId,
            name = name.trim(),
            colorIndex = existing.toLong(),
            claimedByDeviceId = null,
            createdAt = now,
            updatedAt = now,
            deleted = false,
            dirty = true,
        )
        id
    }

    suspend fun renameMember(memberId: String, name: String) = withContext(dispatcher) {
        queries.renameMember(name.trim(), nowMillis(), memberId)
    }

    /**
     * Claiming is exclusive per group: taking a name releases any other name this device had
     * claimed there, so one device can never be two people in the same group.
     */
    suspend fun claimMember(groupId: String, memberId: String) = withContext(dispatcher) {
        val now = nowMillis()
        database.transaction {
            queries.releaseClaimsForDevice(now, groupId, deviceId)
            queries.claimMember(deviceId, now, memberId)
            val member = queries.selectMemberById(memberId).executeAsOneOrNull()
            if (member != null) queries.upsertPref(PREF_DISPLAY_NAME, member.name)
        }
    }

    /**
     * Gives up whichever name this device holds in [groupId], returning it to the pool so
     * somebody else can claim it. The member row and its expenses stay exactly as they are —
     * this is about who is holding the name, not about deleting anyone.
     */
    suspend fun releaseMyIdentity(groupId: String) = withContext(dispatcher) {
        queries.releaseClaimsForDevice(nowMillis(), groupId, deviceId)
    }

    /**
     * The admin freeing somebody else's name, which is the only way a group gets unstuck when
     * a phone is lost, sold, or wiped: without it that name is claimed forever, its owner can
     * never rejoin under it, and nobody else can take it either.
     *
     * The claim is all that goes — the member row, their expenses and their balance are
     * untouched, exactly as when someone gives up their own name. The device that held it
     * finds out on its next pull and lands back on "pick who you are".
     *
     * Admin rights are checked twice on purpose: [GroupDetail.canRelease] decides whether the
     * control is even offered, and `splits_push` re-checks server-side, because hiding a
     * button stops nobody talking to the API directly.
     */
    suspend fun releaseMemberIdentity(memberId: String) = withContext(dispatcher) {
        queries.releaseClaimOfMember(nowMillis(), memberId)
    }

    /** A member can only be removed while they are not entangled in any expense. */
    suspend fun removeMember(groupId: String, memberId: String): Boolean = withContext(dispatcher) {
        val expenses = queries.selectExpensesOfGroup(groupId).executeAsList()
        val splits = queries.selectSplitsOfGroup(groupId).executeAsList()
        val referenced = expenses.any { it.paidByMemberId == memberId } ||
            splits.any { it.memberId == memberId }
        if (referenced) return@withContext false
        queries.softDeleteMember(nowMillis(), memberId)
        true
    }

    /**
     * [preferredId] is the deterministic id of a suggested settlement (see `settlementId`):
     * both people looking at the same suggestion derive it, so recording it on two phones
     * writes one row instead of counting the payment twice.
     *
     * It is only taken when nothing is already stored under it. A row that is *already here*
     * means the user can see the first payment and is deliberately recording a second one, so
     * that gets an id of its own — the merge is only ever for the concurrent case, where the
     * other device's row has not arrived yet.
     *
     * [createdByMemberId] is the member this device holds in the group and is stamped on a new
     * entry only. An edit keeps whoever added the row — including null on rows from before
     * entries had an owner, which stay ownerless rather than being claimed by their first
     * editor.
     */
    suspend fun saveExpense(
        groupId: String,
        expenseId: String?,
        title: String,
        amountMinor: Long,
        paidByMemberId: String,
        kind: ExpenseKind,
        categoryId: String?,
        note: String?,
        occurredAt: Long,
        splits: List<Split>,
        preferredId: String? = null,
        createdByMemberId: String? = null,
    ): String = withContext(dispatcher) {
        val now = nowMillis()
        val unusedPreferredId = preferredId
            ?.takeIf { queries.selectExpenseById(it).executeAsOneOrNull() == null }
        val id = expenseId ?: unusedPreferredId ?: newId()
        val existing = expenseId?.let { queries.selectExpenseById(it).executeAsOneOrNull() }
        val createdAt = existing?.createdAt ?: now
        val creator = if (existing != null) existing.createdByMemberId else createdByMemberId

        database.transaction {
            queries.upsertExpense(
                id = id,
                groupId = groupId,
                title = title.trim(),
                amountMinor = amountMinor,
                paidByMemberId = paidByMemberId,
                kind = kind.name,
                categoryId = categoryId,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                occurredAt = occurredAt,
                createdAt = createdAt,
                updatedAt = now,
                deleted = false,
                dirty = true,
                createdByMemberId = creator,
            )
            queries.deleteSplitsOfExpense(id)
            splits.filter { it.shareMinor != 0L }.forEach { split ->
                queries.insertSplit(id, split.memberId, split.shareMinor)
            }
            // Bumping the group keeps the home screen ordered by real activity.
            queries.touchGroup(now, groupId)
        }
        id
    }

    suspend fun deleteExpense(expenseId: String) = withContext(dispatcher) {
        database.transaction {
            queries.softDeleteExpense(nowMillis(), expenseId)
            // The tombstone is what other devices need; the share rows are dead weight and go
            // immediately, here and on the server.
            queries.deleteSplitsOfExpense(expenseId)
        }
    }

    // ------------------------------------------------------- shelf & lifecycle --

    suspend fun setArchived(groupId: String, archived: Boolean) = withContext(dispatcher) {
        queries.setGroupArchived(archived, groupId)
    }

    suspend fun setHidden(groupId: String, hidden: Boolean) = withContext(dispatcher) {
        queries.setGroupHidden(hidden, groupId)
    }

    /** Destroys the group for everyone. The caller is responsible for checking admin rights. */
    suspend fun deleteGroup(groupId: String) = withContext(dispatcher) {
        database.transaction {
            queries.softDeleteGroup(nowMillis(), groupId)
            queries.deleteSplitsOfGroup(groupId)
            queries.deleteExpensesOfGroup(groupId)
            queries.deleteMembersOfGroup(groupId)
        }
    }

    // -------------------------------------------------------------------- sync --

    suspend fun lastPulledAt(): Long = withContext(dispatcher) {
        queries.selectPref(PREF_LAST_PULL).executeAsOneOrNull()?.toLongOrNull() ?: 0L
    }

    suspend fun setLastPulledAt(value: Long) = withContext(dispatcher) {
        queries.upsertPref(PREF_LAST_PULL, value.toString())
    }

    /**
     * How many rows are waiting to be uploaded. `SyncEngine` watches this so a save reaches the
     * server on its own — without it, an edit sat on the device until the next manual refresh.
     */
    fun observeDirtyCount(): Flow<Long> =
        queries.countDirty().asFlow().mapToOne(dispatcher)

    suspend fun knownGroupIds(): List<String> = withContext(dispatcher) {
        queries.selectAllGroupIds().executeAsList()
    }

    /** Everything edited on this device since the last successful push. */
    suspend fun collectDirty(): PushPayload = withContext(dispatcher) {
        val groups = queries.selectDirtyGroups().executeAsList()
        val members = queries.selectDirtyMembers().executeAsList()
        val expenses = queries.selectDirtyExpenses().executeAsList()

        PushPayload(
            groups = groups.map { row ->
                RemoteGroup(
                    id = row.id,
                    name = row.name,
                    emoji = row.emoji,
                    currencyCode = row.currencyCode,
                    inviteCode = row.inviteCode,
                    adminMemberId = row.adminMemberId,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    deleted = row.deleted,
                    simplifyDebts = row.simplifyDebts,
                )
            },
            members = members.map { row ->
                RemoteMember(
                    id = row.id,
                    groupId = row.groupId,
                    name = row.name,
                    colorIndex = row.colorIndex.toInt(),
                    claimedByDeviceId = row.claimedByDeviceId,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    deleted = row.deleted,
                )
            },
            expenses = expenses.map { row ->
                RemoteExpense(
                    id = row.id,
                    groupId = row.groupId,
                    title = row.title,
                    amountMinor = row.amountMinor,
                    paidByMemberId = row.paidByMemberId,
                    kind = row.kind,
                    categoryId = row.categoryId,
                    note = row.note,
                    occurredAt = row.occurredAt,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    deleted = row.deleted,
                    createdByMemberId = row.createdByMemberId,
                )
            },
            // Shares ride along with their expense; the server replaces the whole set.
            shares = expenses.flatMap { expense ->
                queries.selectSplitsOfExpense(expense.id).executeAsList().map { split ->
                    RemoteShare(expense.id, split.memberId, split.shareMinor)
                }
            },
        )
    }

    suspend fun markPushed(payload: PushPayload) = withContext(dispatcher) {
        database.transaction {
            payload.groups.forEach { queries.clearGroupDirty(it.id) }
            payload.members.forEach { queries.clearMemberDirty(it.id) }
            payload.expenses.forEach { queries.clearExpenseDirty(it.id) }

            // The server has the deletion now, so the local tombstone has done its job and the
            // row can go for good. Anything still dirty is left alone.
            queries.purgeSyncedTombstones()
            queries.purgeSyncedMemberTombstones()
            queries.purgeSyncedGroupTombstones()
            queries.purgeOrphanSplits()
        }
    }

    /**
     * Folds a server snapshot into the local database, newest-write-wins per row.
     *
     * Two local-only facts are deliberately preserved: `archived` and `hidden`. They are this
     * device's shelving decisions and must survive a pull, or every refresh would drag hidden
     * groups back onto the user's list.
     */
    suspend fun applyRemote(
        snapshot: RemoteSnapshot,
        ignoreExpenseIds: Set<String> = emptySet(),
        requestedGroupIds: List<String> = emptyList(),
    ): List<ExpenseNotice> = withContext(dispatcher) {
        val notices = mutableListOf<ExpenseNotice>()

        database.transaction {
            snapshot.groups.forEach { remote ->
                val local = queries.selectGroupById(remote.id).executeAsOneOrNull()
                if (local != null && local.updatedAt > remote.updatedAt) return@forEach
                queries.upsertGroup(
                    id = remote.id,
                    name = remote.name,
                    emoji = remote.emoji,
                    currencyCode = remote.currencyCode,
                    inviteCode = remote.inviteCode,
                    adminMemberId = remote.adminMemberId,
                    createdAt = remote.createdAt,
                    updatedAt = remote.updatedAt,
                    archived = local?.archived ?: false,
                    hidden = local?.hidden ?: false,
                    deleted = remote.deleted,
                    dirty = false,
                    simplifyDebts = remote.simplifyDebts,
                )
            }

            snapshot.members.forEach { remote ->
                val local = queries.selectMemberById(remote.id).executeAsOneOrNull()
                if (local != null && local.updatedAt > remote.updatedAt) return@forEach
                queries.upsertMember(
                    id = remote.id,
                    groupId = remote.groupId,
                    name = remote.name,
                    colorIndex = remote.colorIndex.toLong(),
                    claimedByDeviceId = remote.claimedByDeviceId,
                    createdAt = remote.createdAt,
                    updatedAt = remote.updatedAt,
                    deleted = remote.deleted,
                    dirty = false,
                )
            }

            val sharesByExpense = snapshot.shares.groupBy { it.expenseId }

            snapshot.expenses.forEach { remote ->
                val local = queries.selectExpenseById(remote.id).executeAsOneOrNull()
                if (local != null && local.updatedAt > remote.updatedAt) return@forEach

                // Work out what to tell the user *before* the row is overwritten, and skip
                // anything this device just pushed — you should not be notified of your own
                // edit coming back to you.
                if (remote.id !in ignoreExpenseIds) {
                    val kind = when {
                        remote.deleted -> NoticeKind.Removed
                        local == null -> NoticeKind.Added
                        else -> NoticeKind.Updated
                    }
                    val changed = local == null || local.updatedAt != remote.updatedAt ||
                        local.deleted != remote.deleted
                    if (changed) {
                        val group = queries.selectGroupById(remote.groupId).executeAsOneOrNull()
                        val actor = queries.selectMemberById(remote.paidByMemberId)
                            .executeAsOneOrNull()
                        if (group != null) {
                            notices += ExpenseNotice(
                                expenseId = remote.id,
                                groupId = remote.groupId,
                                groupName = group.name,
                                groupEmoji = group.emoji,
                                title = remote.title,
                                amountMinor = remote.amountMinor,
                                currencyCode = group.currencyCode,
                                actorName = actor?.name ?: "Someone",
                                kind = kind,
                            )
                        }
                    }
                }

                queries.upsertExpense(
                    id = remote.id,
                    groupId = remote.groupId,
                    title = remote.title,
                    amountMinor = remote.amountMinor,
                    paidByMemberId = remote.paidByMemberId,
                    kind = remote.kind,
                    categoryId = remote.categoryId,
                    note = remote.note,
                    occurredAt = remote.occurredAt,
                    createdAt = remote.createdAt,
                    updatedAt = remote.updatedAt,
                    deleted = remote.deleted,
                    dirty = false,
                    createdByMemberId = remote.createdByMemberId,
                )
                // Replace the share set wholesale — an edit may have dropped a participant.
                queries.deleteSplitsOfExpense(remote.id)
                sharesByExpense[remote.id].orEmpty().forEach { share ->
                    queries.insertSplit(remote.id, share.memberId, share.shareMinor)
                }
            }

            notices += reconcileDeletions(snapshot, requestedGroupIds)
            queries.purgeOrphanSplits()
        }

        notices
    }

    /**
     * Removes local rows the server no longer has.
     *
     * Deletions are permanent server-side, so nothing arrives to say "this was deleted" — the
     * id simply stops appearing in the pull's live-id lists. Comparing against those lists is
     * how an offline device catches up.
     *
     * Two guards make this safe:
     *  - `dirty = 0` only. A row this device created or deleted but has not pushed yet is not
     *    on the server *because we have not sent it*, not because it was deleted.
     *  - Only groups the snapshot actually reported on. A null list means an older server that
     *    does not send them, and reconciling on absent data would wipe the database.
     */
    private fun reconcileDeletions(
        snapshot: RemoteSnapshot,
        requestedGroupIds: List<String>,
    ): List<ExpenseNotice> {
        val liveGroups = snapshot.liveGroupIds ?: return emptyList()
        val liveMembers = snapshot.liveMemberIds?.toSet() ?: return emptyList()
        val liveExpenses = snapshot.liveExpenseIds?.toSet() ?: return emptyList()
        val notices = mutableListOf<ExpenseNotice>()

        // A group the admin deleted is simply absent from live_group_ids, so it can only be
        // spotted by comparing against what we asked about. Without this the group would sit
        // on the device forever, since nothing in the response ever mentions it again.
        val liveGroupSet = liveGroups.toSet()
        for (groupId in requestedGroupIds) {
            if (groupId in liveGroupSet) continue
            val local = queries.selectGroupById(groupId).executeAsOneOrNull() ?: continue
            // Never remove a group this device has not managed to push yet — it is missing
            // from the server because we have not sent it, not because it was deleted.
            if (local.dirty) continue

            queries.deleteSplitsOfGroup(groupId)
            queries.deleteExpensesOfGroup(groupId)
            queries.deleteMembersOfGroup(groupId)
            queries.hardDeleteGroup(groupId)
        }

        for (groupId in liveGroups) {
            val group = queries.selectGroupById(groupId).executeAsOneOrNull() ?: continue

            queries.selectExpenseIdsOfGroup(groupId).executeAsList().forEach { expenseId ->
                if (expenseId in liveExpenses) return@forEach
                val row = queries.selectExpenseById(expenseId).executeAsOneOrNull()
                if (row != null && !row.deleted) {
                    notices += ExpenseNotice(
                        expenseId = row.id,
                        groupId = groupId,
                        groupName = group.name,
                        groupEmoji = group.emoji,
                        title = row.title,
                        amountMinor = row.amountMinor,
                        currencyCode = group.currencyCode,
                        actorName = queries.selectMemberById(row.paidByMemberId)
                            .executeAsOneOrNull()?.name ?: "Someone",
                        kind = NoticeKind.Removed,
                    )
                }
                queries.deleteSplitsOfExpense(expenseId)
                queries.hardDeleteExpense(expenseId)
            }

            queries.selectMemberIdsOfGroup(groupId).executeAsList().forEach { memberId ->
                if (memberId !in liveMembers) queries.hardDeleteMember(memberId)
            }
        }

        return notices
    }

    suspend fun lastPurgeAt(): Long = withContext(dispatcher) {
        queries.selectPref(PREF_LAST_PURGE).executeAsOneOrNull()?.toLongOrNull() ?: 0L
    }

    suspend fun setLastPurgeAt(value: Long) = withContext(dispatcher) {
        queries.upsertPref(PREF_LAST_PURGE, value.toString())
    }

    /**
     * Drops local tombstones that have aged past [cutoffMillis], plus any orphaned shares.
     *
     * Rows still marked dirty are left alone however old they are: a deletion that has not been
     * pushed yet is the only record that it happened.
     */
    suspend fun purgeLocalTombstones(cutoffMillis: Long) = withContext(dispatcher) {
        database.transaction {
            queries.purgeOrphanSplits()
            queries.purgeDeletedExpenses(cutoffMillis)
            queries.purgeDeletedMembers(cutoffMillis)
            queries.purgeDeletedGroups(cutoffMillis)
        }
    }

    /** The highest server timestamp in a snapshot — the watermark for the next pull. */
    fun watermarkOf(snapshot: RemoteSnapshot): Long = maxOf(
        snapshot.groups.maxOfOrNull { it.updatedAt } ?: 0L,
        snapshot.members.maxOfOrNull { it.updatedAt } ?: 0L,
        snapshot.expenses.maxOfOrNull { it.updatedAt } ?: 0L,
    )

    // ----------------------------------------------------------------- helpers --

    private fun buildCards(
        groups: List<GroupEntity>,
        members: List<MemberEntity>,
        expenses: List<ExpenseEntity>,
        splits: List<SplitEntity>,
    ): List<GroupCard> {
        val membersByGroup = members.groupBy { it.groupId }
        val expensesByGroup = expenses.groupBy { it.groupId }
        val splitsByExpense = splits.groupBy { it.expenseId }

        return groups.map { row ->
            val groupMembers = membersByGroup[row.id].orEmpty().map { it.toModel() }
            val groupExpenses = expensesByGroup[row.id].orEmpty()
                .map { expense -> expense.toModel(splitsByExpense[expense.id].orEmpty()) }
            val me = groupMembers.firstOrNull { it.claimedByDeviceId == deviceId }

            GroupCard(
                group = row.toModel(),
                members = groupMembers,
                summary = summarise(groupMembers, groupExpenses),
                myMemberId = me?.id,
                expenseCount = groupExpenses.count { it.kind == ExpenseKind.EXPENSE },
                lastActivityAt = groupExpenses.maxOfOrNull { it.updatedAt } ?: row.updatedAt,
            )
        }
    }
}

/** Rolls every joined group's balance up per currency for the home screen header. */
fun List<GroupCard>.overallPosition(): OverallPosition {
    val byCurrency = LinkedHashMap<String, Long>()
    for (card in this) {
        if (card.myMemberId == null || card.group.archived) continue
        val code = card.group.currencyCode
        byCurrency[code] = (byCurrency[code] ?: 0L) + card.myBalanceMinor
    }
    return OverallPosition(byCurrency)
}

// ------------------------------------------------------------------- mapping --

private fun GroupEntity.toModel() = Group(
    id = id,
    name = name,
    emoji = emoji,
    currencyCode = currencyCode,
    inviteCode = inviteCode,
    adminMemberId = adminMemberId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archived = archived,
    hidden = hidden,
    simplifyDebts = simplifyDebts,
)

private fun MemberEntity.toModel() = Member(
    id = id,
    groupId = groupId,
    name = name,
    colorIndex = colorIndex.toInt(),
    claimedByDeviceId = claimedByDeviceId,
)

private fun ExpenseEntity.toModel(splits: List<SplitEntity>) = Expense(
    id = id,
    groupId = groupId,
    title = title,
    amountMinor = amountMinor,
    paidByMemberId = paidByMemberId,
    kind = ExpenseKind.fromDb(kind),
    categoryId = categoryId,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    splits = splits.map { Split(it.memberId, it.shareMinor) },
    createdByMemberId = createdByMemberId,
)
