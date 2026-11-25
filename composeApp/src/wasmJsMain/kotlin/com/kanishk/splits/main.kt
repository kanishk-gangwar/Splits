package com.kanishk.splits

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.window.ComposeViewport
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.js.Promise

/**
 * Compose on the web draws text itself and cannot reach the system's emoji font, so without
 * this every group icon and category renders as an empty box. Versioned URL, so the service
 * worker can cache it forever after the first launch.
 */
private const val EMOJI_FONT_URL =
    "https://cdn.jsdelivr.net/gh/googlefonts/noto-emoji@v2.047/fonts/NotoColorEmoji.ttf"

private const val EMOJI_WAIT_MS = 4_000L

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    MainScope().launch {
        // sql.js and the saved database load asynchronously; the driver itself is synchronous,
        // so everything has to be in place before the first composition touches the repository.
        initSqlite().await<JsAny?>()

        // An invite opened as /app/#CODE (the join page's iPhone button). Handed over the same
        // way Android hands over a splits:// link, then dropped from the address bar so a
        // reload does not offer the same invite again.
        takeInviteFromHash()?.let { DeepLinks.offer("splits://join/$it") }

        ComposeViewport(viewportContainerId = "app") {
            // The first frame waits for the emoji font, because text laid out before it arrives
            // keeps its empty boxes for good. But it waits at most EMOJI_WAIT_MS: the font is
            // 10 MB, and on iPhone loading it can stall, which used to leave the app on a blank
            // screen forever. Past the limit the app opens anyway and the font carries on
            // loading for the screens that come after.
            val resolver = LocalFontFamilyResolver.current
            var ready by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                val load = launch {
                    runCatching {
                        val bytes = fetchBytes(EMOJI_FONT_URL)
                        resolver.preload(FontFamily(Font("NotoColorEmoji", bytes)))
                    }.onFailure { println("Emoji font not loaded: $it") }
                }
                withTimeoutOrNull(EMOJI_WAIT_MS) { load.join() }
                ready = true
                hideSplash()
            }
            if (ready) App()
        }
    }
}

private suspend fun fetchBytes(url: String): ByteArray {
    val buffer = jsFetchBytes(url).await<JsAny>()
    val size = jsLength(buffer)
    return ByteArray(size) { jsByteAt(buffer, it).toByte() }
}

private fun jsFetchBytes(url: String): Promise<JsAny> = js(
    "fetch(url).then(function (r) { if (!r.ok) throw new Error(r.status); return r.arrayBuffer(); })" +
        ".then(function (b) { return new Int8Array(b); })"
)

private fun jsLength(array: JsAny): Int = js("array.length")

private fun jsByteAt(array: JsAny, index: Int): Int = js("array[index]")

private fun takeInviteFromHash(): String? = js(
    """(function () {
    var code = location.hash.replace(/^#/, '');
    if (!code) return null;
    history.replaceState(null, '', location.pathname + location.search);
    return code;
})()"""
)

private fun initSqlite(): Promise<JsAny?> = js("globalThis.splitsSql.init()")

// The fade-out, and waiting for the intro animation to finish, live in index.html.
private fun hideSplash(): Unit = js("{ if (globalThis.splitsHideSplash) globalThis.splitsHideSplash(); }")
