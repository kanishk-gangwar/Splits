package com.kanishk.splits

import com.kanishk.splits.model.AppVersion
import com.kanishk.splits.model.isUpdateAvailable
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The update prompt is only as good as this comparison. Getting it wrong is silent in both
 * directions: everyone stays on an old build, or everyone is nagged forever.
 */
class AppVersionTest {

    @Test
    fun `a newer release is offered`() {
        assertTrue(isUpdateAvailable("v1.5.0", "1.4.1"))
        assertTrue(isUpdateAvailable("1.5.0", "1.4.9"))
        assertTrue(isUpdateAvailable("2.0.0", "1.99.99"))
    }

    @Test
    fun `the same version is not an update`() {
        assertFalse(isUpdateAvailable("v1.5.0", "1.5.0"))
        // Trailing zeroes are the same version, not a newer one.
        assertFalse(isUpdateAvailable("1.5", "1.5.0"))
        assertFalse(isUpdateAvailable("1.5.0", "1.5"))
    }

    /** A dev build ahead of the last release should not be told to downgrade. */
    @Test
    fun `an older release is never offered`() {
        assertFalse(isUpdateAvailable("v1.4.1", "1.5.0"))
    }

    /**
     * The one that string comparison gets wrong: "1.10.0" sorts before "1.5.0" as text, which
     * would leave everyone on 1.5.0 believing they were current forever.
     */
    @Test
    fun `double-digit segments compare as numbers`() {
        assertTrue(isUpdateAvailable("1.10.0", "1.5.0"))
        assertFalse(isUpdateAvailable("1.5.0", "1.10.0"))
    }

    @Test
    fun `anything unparseable is not an update`() {
        // Whatever this is, it is not a version, and guessing would be worse than saying
        // nothing — the caller falls back to offering the plain download link.
        assertFalse(isUpdateAvailable("nightly-3f2a1c", "1.5.0"))
        assertFalse(isUpdateAvailable("v2.0.0-rc1", "1.5.0"))
        assertFalse(isUpdateAvailable("v1.5.0", ""))
        assertFalse(isUpdateAvailable("", "1.5.0"))
    }

    @Test
    fun `tags parse with or without the v`() {
        assertTrue(AppVersion.parse("v1.5.0") == AppVersion.parse("1.5.0"))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("v"))
    }
}
