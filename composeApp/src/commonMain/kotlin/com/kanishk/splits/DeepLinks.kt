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
 * The public page that resolves an invite in a browser. It is swapped for the real host in
 * phase two; the `splits://` fallback below always works even before that page exists.
 */
const val INVITE_WEB_BASE = "https://kanishk-splits.pages.dev/join"

fun inviteLink(code: String): String = "$INVITE_WEB_BASE#$code"

fun inviteDeepLink(code: String): String = "splits://join/$code"

fun inviteShareMessage(groupName: String, code: String): String = buildString {
    append("Join \"")
    append(groupName)
    append("\" on Splits\n\n")
    append(inviteLink(code))
    append("\n\nOr open the app and enter code: ")
    append(code)
}
