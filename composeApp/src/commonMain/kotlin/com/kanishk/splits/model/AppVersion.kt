package com.kanishk.splits.model

/**
 * Comparing "1.5.0" against "v1.10.0" and getting the right answer.
 *
 * It is here rather than inlined in the settings screen for the usual reason: a composable
 * cannot be asserted on and this is exactly the kind of thing that is quietly wrong. String
 * comparison says 1.10.0 is older than 1.5.0, which would tell everyone on 1.5.0 that they are
 * up to date forever.
 */
data class AppVersion(val parts: List<Int>) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        val length = maxOf(parts.size, other.parts.size)
        for (i in 0 until length) {
            // A missing segment is zero, so 1.5 and 1.5.0 are the same version rather than the
            // shorter one always losing.
            val mine = parts.getOrElse(i) { 0 }
            val theirs = other.parts.getOrElse(i) { 0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }
        return 0
    }

    companion object {
        /**
         * Accepts what a release tag and a version name actually look like: `v1.5.0`, `1.5.0`,
         * `1.5`. Anything with a segment that is not a number — a pre-release suffix, a hash,
         * an error page's worth of text — returns null, and the caller shows the plain download
         * link rather than guessing at a comparison.
         */
        fun parse(raw: String): AppVersion? {
            val trimmed = raw.trim().removePrefix("v").removePrefix("V")
            if (trimmed.isEmpty()) return null
            val parts = trimmed.split('.').map { segment ->
                segment.toIntOrNull()?.takeIf { it >= 0 } ?: return null
            }
            return AppVersion(parts)
        }
    }
}

/**
 * Whether [latest] is worth telling the user about. False whenever either side is unparseable
 * or the installed build is the same or ahead — a debug build sitting ahead of the last release
 * should not be nagged, and neither should anyone the check simply failed for.
 */
fun isUpdateAvailable(latest: String, installed: String): Boolean {
    val newest = AppVersion.parse(latest) ?: return false
    val mine = AppVersion.parse(installed) ?: return false
    return newest > mine
}
