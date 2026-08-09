package com.kanishk.splits

import com.kanishk.splits.data.isInviteCodeShaped
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
 *
 * Length is decided by [isInviteCodeShaped] rather than spelled out here, so lengthening a
 * code cannot leave this behind. A full URL survives the alphabet filter as a much longer
 * string than any code, which is what stops the whole link matching as one.
 */
fun parseInvite(rawLink: String?): String? {
    if (rawLink.isNullOrBlank()) return null

    val tokens = rawLink.split(' ', '\n', '\r', '\t').filter { it.isNotBlank() }

    // A link that actually addresses the join route is the only thing worth taking apart. This
    // is also what makes a whole forwarded invite message work: the message is many tokens and
    // exactly one of them is the link, so the surrounding prose is never examined. It has to be
    // this way round — "expenses" survives the alphabet filter as eight characters and would
    // otherwise read as a perfectly good legacy code.
    for (token in tokens.filter { it.contains("join", ignoreCase = true) }) {
        val candidates = listOf(
            token.substringAfterLast('#', ""),
            token.substringAfterLast("c=", "").substringBefore('&'),
            token.trimEnd('/').substringAfterLast('/'),
        )
        for (candidate in candidates) {
            val code = normaliseInviteCode(candidate)
            if (isInviteCodeShaped(code)) return code
        }
    }

    // A code pasted on its own. Only when that is the whole input — picking a code-shaped word
    // out of a sentence guesses at what the user meant, and guessing wrong sends them into a
    // join flow for a group that does not exist.
    if (tokens.size == 1) {
        val code = normaliseInviteCode(tokens[0])
        if (isInviteCodeShaped(code)) return code
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

/**
 * For the share action in the home top bar — "get the app", with no group attached.
 *
 * It shares the download *link* rather than the installed APK file. That is not a shortcut:
 * the link always resolves to the newest release, whereas a passed-around APK is frozen at
 * whatever version the sender happened to have, and the whole point of shipping 1.3.0 was that
 * older builds must not stay in circulation.
 */
fun appShareMessage(): String = buildString {
    append("Splits — split expenses with friends. No accounts, no phone numbers.\n\n")
    append("Get it for Android:\n")
    append(APP_DOWNLOAD_URL)
}

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
