package com.kanishk.splits.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanishk.splits.LocalSyncEngine
import com.kanishk.splits.data.sync.SyncStatus
import kotlinx.coroutines.launch

/**
 * Swipe down anywhere on a list to reconcile with the server.
 *
 * The gesture is available even when sync is switched off — it just resolves instantly. That
 * is deliberate: a refresh control that vanishes depending on configuration is more confusing
 * than one that quietly does nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncRefreshBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val syncEngine = LocalSyncEngine.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                syncEngine.syncNow()
                refreshing = false
            }
        },
        modifier = modifier,
        content = content,
    )
}

/** Surfaces a failed sync without stealing the screen — local data is still perfectly usable. */
@Composable
fun SyncFailureStrip(modifier: Modifier = Modifier) {
    val syncEngine = LocalSyncEngine.current
    val status by syncEngine.status.collectAsStateWithLifecycle()
    val failed = status as? SyncStatus.Failed

    AnimatedVisibility(visible = failed != null, modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "Working offline — your changes are saved and will sync on the next refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

/** Small helper so screens can dim content while a sync is in flight, if they want to. */
@Composable
fun rememberSyncing(): Boolean {
    val syncEngine = LocalSyncEngine.current
    val status by syncEngine.status.collectAsStateWithLifecycle()
    return status is SyncStatus.Syncing
}
