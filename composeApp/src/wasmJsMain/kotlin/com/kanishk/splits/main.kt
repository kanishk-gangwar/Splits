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
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlin.js.Promise

/**
 * Compose on the web draws text itself and cannot reach the system's emoji font, so without
 * this every group icon and category renders as an empty box. Versioned URL, so the service
 * worker can cache it forever after the first launch.
 */
private const val EMOJI_FONT_URL =
    "https://cdn.jsdelivr.net/gh/googlefonts/noto-emoji@v2.047/fonts/NotoColorEmoji.ttf"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    MainScope().launch {
        val emoji = async { runCatching { fetchBytes(EMOJI_FONT_URL) }.getOrNull() }
        // sql.js and the saved database load asynchronously; the driver itself is synchronous,
        // so everything has to be in place before the first composition touches the repository.
        initSqlite().await<JsAny?>()
        val emojiBytes = emoji.await()

        // An invite opened as /app/#CODE (the join page's iPhone button). Handed over the same
        // way Android hands over a splits:// link, then dropped from the address bar so a
        // reload does not offer the same invite again.
        takeInviteFromHash()?.let { DeepLinks.offer("splits://join/$it") }

        ComposeViewport(viewportContainerId = "app") {
            val resolver = LocalFontFamilyResolver.current
            var fontsReady by remember { mutableStateOf(emojiBytes == null) }
            LaunchedEffect(Unit) {
                if (emojiBytes != null) {
                    runCatching { resolver.preload(FontFamily(Font("NotoColorEmoji", emojiBytes))) }
                    fontsReady = true
                }
            }
            if (fontsReady) App()
        }
        hideSplash()
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

private fun hideSplash(): Unit = js("{ var s = document.getElementById('splash'); if (s) s.remove(); }")
