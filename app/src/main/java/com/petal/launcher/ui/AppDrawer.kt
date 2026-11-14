@file:OptIn(ExperimentalFoundationApi::class)

package com.petal.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.petal.launcher.*
import com.petal.launcher.data.*
import kotlinx.coroutines.delay

/** Full screen app drawer: live search, Flowerpot category chips, web-search fallback. */
@Composable
fun AppDrawer(vm: LauncherViewModel, s: LauncherState, apps: List<AppInfo>) {
    val ctx = LocalContext.current
    val st = s.settings
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<AppCategory?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (vm.drawerFocusSearch) { delay(300); runCatching { focus.requestFocus() } }
    }

    val visible = remember(apps, s.hidden) { apps.filter { it.pkg !in s.hidden } }
    val categories = remember(visible) { visible.map { it.category }.distinct().sortedBy { it.ordinal } }
    val q = query.trim()
    val filtered = remember(visible, q, category) {
        visible.filter { app ->
            (category == null || app.category == category) &&
                (q.isEmpty() || app.label.contains(q, true) || app.pkg.contains(q, true))
        }
    }
    val onSurface = MaterialTheme.colorScheme.onSurface

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)) {
        Column(Modifier.systemBarsPadding().imePadding()) {
            // Header (drag down to close)
            Column(
                Modifier.pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > 160f) vm.overlay = Overlay.NONE },
                        onVerticalDrag = { _, dy -> total += dy }
                    )
                }
            ) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
                    singleLine = true, shape = RoundedCornerShape(28.dp),
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
                        }
                    }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                    }
                    items(categories) { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = if (category == c) null else c },
                            label = { Text(c.label) }
                        )
                    }
                }
            }

            if (q.isNotEmpty()) {
                ListItem(
                    headlineContent = { Text("Search \"$q\" on ${st.searchEngine.label}") },
                    leadingContent = { Icon(Icons.Filled.Language, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { openWebSearch(ctx, st.searchEngine, q) }
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(st.drawerColumns),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (filtered.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "No apps found", Modifier.fillMaxWidth().padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(filtered, key = { it.pkg }) { app ->
                    AppCell(
                        app, st.iconShape, st.showDrawerLabels, onSurface, false,
                        onClick = { launchApp(ctx, app.pkg); vm.overlay = Overlay.NONE },
                        onLongClick = { vm.appSheet = app },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}
