package com.kanishk.splits.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * A thin wrapper over the four Postgres functions. There is no PostgREST table access at
 * all — RLS blocks it — so this is the entire server surface the app can reach.
 */
class SplitsApi(
    private val config: SupabaseConfig = SupabaseConfig,
    engine: HttpClient = defaultHttpClient(),
) {
    private val client = engine

    val isConfigured: Boolean get() = config.isConfigured

    suspend fun pull(groupIds: List<String>, since: Long): Result<RemoteSnapshot> =
        rpc("splits_pull", PullArgs(groupIds, since))

    suspend fun resolveInvite(inviteCode: String): Result<RemoteSnapshot> =
        rpc("splits_resolve_invite", InviteArgs(inviteCode))

    suspend fun push(payload: PushPayload): Result<RpcResult> =
        rpc("splits_push", PushArgs(payload))

    suspend fun deleteGroup(groupId: String, deviceId: String): Result<RpcResult> =
        rpc("splits_delete_group", DeleteGroupArgs(groupId, deviceId))

    /** Hard-deletes tombstones that have aged past the retention window. */
    suspend fun purgeDeleted(retentionDays: Int): Result<RpcResult> =
        rpc("splits_purge_deleted", PurgeArgs(retentionDays))

    private suspend inline fun <reified Args, reified Out> rpc(
        function: String,
        args: Args,
    ): Result<Out> {
        if (!config.isConfigured) {
            return Result.failure(SyncNotConfigured)
        }
        return runCatching {
            val response: HttpResponse = client.post("${config.rpcBase}/$function") {
                header("apikey", config.ANON_KEY)
                header("Authorization", "Bearer ${config.ANON_KEY}")
                contentType(ContentType.Application.Json)
                setBody(args)
            }
            if (!response.status.isSuccess()) {
                throw SyncHttpError(response.status.value, response.status.description)
            }
            response.body<Out>()
        }
    }
}

/** Thrown when the app has no Supabase project wired up yet — never surfaced as an error. */
object SyncNotConfigured : Throwable("Supabase is not configured")

class SyncHttpError(val code: Int, val detail: String) : Throwable("HTTP $code: $detail")

internal val SyncJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

fun defaultHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) { json(SyncJson) }
}
