package com.kanishk.splits.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Asks GitHub what the newest release is, so the settings screen can say whether there is
 * anything to download rather than offering an update button that means nothing.
 *
 * This is the one call in the app that does not go to Supabase, and it is deliberately
 * unauthenticated and read-only: the endpoint is public, and sending a token from a shipped
 * APK would be handing it out. Unauthenticated GitHub allows 60 calls an hour per IP, which is
 * far more than opening a settings screen will ever use.
 *
 * Nothing here is trusted with anything. The reply decides one thing — whether to show "update
 * available" — and the tag it carries is parsed by [com.kanishk.splits.model.AppVersion.parse],
 * which rejects anything that is not a plain dotted number. The download URL is the constant in
 * DeepLinks.kt, never a URL taken from the response.
 */
private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/kanishk-gangwar/Splits/releases/latest"

@Serializable
private data class LatestRelease(
    @SerialName("tag_name") val tagName: String = "",
)

/**
 * One client for the process, like the sync client. Building a fresh one per settings visit
 * would leak an engine and its thread pool every time the screen is opened.
 */
private val sharedClient by lazy { defaultHttpClient() }

class ReleaseCheck(private val client: HttpClient = sharedClient) {

    /**
     * The newest release's tag, or a failure. Failure is an ordinary outcome here — no network,
     * rate limited, GitHub down — and the caller falls back to offering the download link
     * without a verdict, because "we could not check" must never read as "you are up to date".
     */
    suspend fun latestTag(): Result<String> = runCatching {
        val response: HttpResponse = client.get(LATEST_RELEASE_URL) {
            header("Accept", "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) {
            throw SyncHttpError(response.status.value, response.status.description)
        }
        response.body<LatestRelease>().tagName
    }
}
