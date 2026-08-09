package com.kanishk.splits

import com.kanishk.splits.data.normaliseInviteCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where a shared invite lands, whichever platform delivered it. */
object DeepLinks {
    private val _pendingInvite = MutableStateFlow<String?>(null)
    val pendingInvite: StateFlow<String?> = _pendingInvite

    fun offer(rawLink: String?) {
        val code = parseInvite(rawLink) ?: return
        _pendingInvite.value = code
    }

    fun consume() {
        _pendingInvite.value = null
    }
}

/**
 * Accepts every shape the invite can arrive in:
 *   splits://join/AB12CD34
 *   https://<host>/join#AB12CD34
 *   https://<host>/join?c=AB12CD34
 *   AB12CD34            (pasted by hand)
 */
fun parseInvite(rawLink: String?): String? {
    if (rawLink.isNullOrBlank()) return null

    val afterFragment = rawLink.substringAfterLast('#', "")
    val afterQuery = rawLink.substringAfterLast("c=", "").substringBefore('&')
    val afterPath = rawLink.trimEnd('/').substringAfterLast('/')

    val candidates = listOf(afterFragment, afterQuery, afterPath, rawLink)
    for (candidate in candidates) {
        val code = normaliseInviteCode(candidate)
        if (code.length == 8) return code
    }
    return null
}

/**
 * The public page that resolves an invite in a browser. Served from `docs/join/` in this repo
 * via GitHub Pages, so there is no hosting bill and nothing to deploy separately.
 *
 * The `splits://` deep link below is what actually opens the app; the web page exists so a
 * shared link still means something to someone who doesn't have Splits installed yet.
 */
const val INVITE_WEB_BASE = "https://kanishk-gangwar.github.io/Splits/join"

fun inviteLink(code: String): String = "$INVITE_WEB_BASE#$code"

fun inviteDeepLink(code: String): String = "splits://join/$code"

/**
 * Always serves the newest release, because every release attaches its APK under this exact
 * filename. Renaming the asset breaks the link, so the release title carries the version
 * instead of the file name.
 */
const val APP_DOWNLOAD_URL =
    "https://github.com/kanishk-gangwar/Splits/releases/latest/download/Splits.apk"

fun inviteShareMessage(groupName: String, code: String): String = buildString {
    append("Join \"")
    append(groupName)
    append("\" on Splits — split expenses without accounts or phone numbers.\n\n")
    append(inviteLink(code))
    append("\n\nAlready have the app? Enter this code: ")
    append(code)
    // Most people receiving an invite will not have the app yet, so the download comes with it
    // rather than being something the sender has to remember to paste separately.
    append("\n\nGet the app for Android:\n")
    append(APP_DOWNLOAD_URL)
}
