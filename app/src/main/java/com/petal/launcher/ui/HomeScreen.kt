@file:OptIn(ExperimentalFoundationApi::class)

package com.petal.launcher.ui

import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.petal.launcher.*
import com.petal.launcher.data.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(vm: LauncherViewModel, s: LauncherState, apps: List<AppInfo>) {
    val ctx = LocalContext.current
    val st = s.settings
    val haptic = LocalHapticFeedback.current
    val appMap = remember(apps) { apps.associateBy { it.pkg } }
    val pagerState = rememberPagerState(pageCount = { s.pages.size })
    var showWebSearch by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState) { snapshotFlow { pagerState.currentPage }.collect { vm.currentPage = it } }
    LaunchedEffect(vm.homeTick) { if (vm.homeTick > 0) pagerState.animateScrollToPage(0) }

    val contentColor = if (st.backdrop == Backdrop.SOLID) MaterialTheme.colorScheme.onBackground else Color.White
    val shadow = st.backdrop != Backdrop.SOLID

    val backdrop = when (st.backdrop) {
        Backdrop.NONE -> Modifier
        Backdrop.SCRIM -> Modifier.background(Color.Black.copy(alpha = 0.28f))
        Backdrop.GRADIENT -> Modifier.background(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, Color.Black.copy(alpha = 0.55f)))
        )
        Backdrop.SOLID -> Modifier.background(MaterialTheme.colorScheme.background)
    }

    val runGesture: (GestureAction) -> Unit = { action ->
        when (action) {
            GestureAction.OPEN_DRAWER -> vm.openDrawer(false)
            GestureAction.OPEN_SEARCH -> vm.openDrawer(true)
            GestureAction.EXPAND_NOTIFICATIONS -> expandNotificationPanel(ctx)
            GestureAction.OPEN_SETTINGS -> vm.openSettings()
            GestureAction.NONE -> Unit
        }
    }

    CompositionLocalProvider(LocalHomeContent provides contentColor) {
        Box(
            Modifier
                .fillMaxSize()
                .then(backdrop)
                .pointerInput(st.doubleTap) {
                    detectTapGestures(
                        onDoubleTap = { runGesture(st.doubleTap) },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.showWorkspaceOptions = true
                        }
                    )
                }
                .pointerInput(st.swipeUp, st.swipeDown) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            when {
                                total < -140f -> runGesture(st.swipeUp)
                                total > 140f -> runGesture(st.swipeDown)
                            }
                        },
                        onVerticalDrag = { _, dy -> total += dy }
                    )
                }
        ) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                if (st.showGlance) {
                    AtAGlance(s, contentColor, shadow) { showReminder = true }
                }

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    HomePage(
                        items = s.pages.getOrElse(page) { emptyList() },
                        appMap = appMap,
                        st = st,
                        contentColor = contentColor,
                        shadow = shadow,
                        onFolder = { vm.openFolderId = it.id },
                        onApp = { launchApp(ctx, it.pkg) },
                        onAppLong = { vm.appSheet = it }
                    )
                }

                PageDots(count = s.pages.size, current = pagerState.currentPage, color = contentColor)

                if (st.showQsb) {
                    Qsb(
                        onSearch = { vm.openDrawer(true) },
                        onWeb = { showWebSearch = true },
                        onSettings = { vm.openSettings() }
                    )
                }

                Dock(
                    dockApps = s.dock.mapNotNull { appMap[it] },
                    shape = st.iconShape,
                    onApp = { launchApp(ctx, it.pkg) },
                    onAppLong = { vm.appSheet = it },
                    onAllApps = { vm.openDrawer(false) }
                )
            }
        }
    }

    if (showWebSearch) {
        WebSearchDialog(st.searchEngine, onDismiss = { showWebSearch = false }) {
            showWebSearch = false
            openWebSearch(ctx, st.searchEngine, it)
        }
    }
    if (showReminder) {
        ReminderDialog(s.reminder, onDismiss = { showReminder = false }) {
            vm.setReminder(it)
            showReminder = false
        }
    }
}

@Composable
private fun HomePage(
    items: List<HomeItem>,
    appMap: Map<String, AppInfo>,
    st: LauncherSettings,
    contentColor: Color,
    shadow: Boolean,
    onFolder: (HomeItem) -> Unit,
    onApp: (AppInfo) -> Unit,
    onAppLong: (AppInfo) -> Unit
) {
    val cols = st.homeColumns
    val rows = HOME_CAPACITY / cols
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        repeat(rows) { r ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(cols) { c ->
                    val item = items.getOrNull(r * cols + c)
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (item != null) {
                            if (item.isFolder) {
                                FolderCell(item, appMap, st.iconShape, st.showHomeLabels, contentColor, shadow) { onFolder(item) }
                            } else {
                                appMap[item.pkg]?.let { app ->
                                    AppCell(app, st.iconShape, st.showHomeLabels, contentColor, shadow,
                                        onClick = { onApp(app) }, onLongClick = { onAppLong(app) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, color: Color) {
    if (count <= 1) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(count) { i ->
            Box(
                Modifier.padding(horizontal = 4.dp).size(if (i == current) 8.dp else 6.dp)
                    .clip(CircleShape).background(color.copy(alpha = if (i == current) 1f else 0.4f))
            )
        }
    }
}

/** Search bar (QSB): in-app search, web search and launcher settings shortcuts. */
@Composable
private fun Qsb(onSearch: () -> Unit, onWeb: () -> Unit, onSettings: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(52.dp)
    ) {
        Row(
            Modifier.clickable(onClick = onSearch).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = "Search apps")
            Spacer(Modifier.width(12.dp))
            Text(
                "Search apps", Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
            )
            IconButton(onClick = onWeb) { Icon(Icons.Filled.Language, contentDescription = "Web search") }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Launcher settings") }
        }
    }
}

/** Hotseat: pinned apps + All Apps button. */
@Composable
private fun Dock(
    dockApps: List<AppInfo>,
    shape: IconShape,
    onApp: (AppInfo) -> Unit,
    onAppLong: (AppInfo) -> Unit,
    onAllApps: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            dockApps.forEach { app ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    AppCell(app, shape, false, MaterialTheme.colorScheme.onSurface, false,
                        onClick = { onApp(app) }, onLongClick = { onAppLong(app) })
                }
            }
            repeat(DOCK_SLOTS - dockApps.size) { Spacer(Modifier.weight(1f)) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(52.dp).clip(shape.toShape())
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onAllApps),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Apps, "All apps", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

private data class BatteryInfo(val percent: Int = 0, val charging: Boolean = false)

private fun Intent.toBattery(): BatteryInfo {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryInfo(if (level >= 0 && scale > 0) level * 100 / scale else 0, charging)
}

/** At a Glance (Smartspace): greeting, clock, date, battery and pinned reminder. */
@Composable
private fun AtAGlance(s: LauncherState, color: Color, shadow: Boolean, onReminder: () -> Unit) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var battery by remember { mutableStateOf(BatteryInfo()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { battery = i.toBattery() }
        }
        val sticky = ContextCompat.registerReceiver(
            ctx, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sticky?.let { battery = it.toBattery() }
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }

    val greeting = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
    val timeFmt = if (s.settings.use24h) "HH:mm" else "h:mm a"
    val time = now.format(DateTimeFormatter.ofPattern(timeFmt, Locale.getDefault()))
    val date = now.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
    val sh = if (shadow) androidx.compose.ui.graphics.Shadow(
        Color.Black.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(0f, 2f), 8f
    ) else null

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(greeting, color = color.copy(alpha = 0.9f), style = TextStyle(fontSize = 14.sp, shadow = sh))
        Text(
            time, color = color,
            style = TextStyle(fontSize = 52.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Light, shadow = sh),
            modifier = Modifier.clickable { openClock(ctx) }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                date, color = color,
                style = TextStyle(fontSize = 16.sp, shadow = sh),
                modifier = Modifier.clickable { openCalendar(ctx) }
            )
            Text(
                "  •  ${battery.percent}%" + if (battery.charging) " ⚡ Charging" else "",
                color = color, style = TextStyle(fontSize = 16.sp, shadow = sh)
            )
        }
        Row(
            Modifier.padding(top = 6.dp).clickable(onClick = onReminder),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PushPin, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                s.reminder.ifBlank { "Tap to add a reminder" },
                color = color.copy(alpha = if (s.reminder.isBlank()) 0.7f else 1f),
                style = TextStyle(fontSize = 14.sp, shadow = sh),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WebSearchDialog(engine: SearchEngine, onDismiss: () -> Unit, onSearch: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search on ${engine.label}") },
        text = {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(q) }),
                modifier = Modifier.focusRequester(focus)
            )
        },
        confirmButton = { TextButton(onClick = { onSearch(q) }) { Text("Search") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ReminderDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick reminder") },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text("e.g. Pay electricity bill") }, maxLines = 3
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { onSave("") }) { Text("Clear") } }
    )
}
