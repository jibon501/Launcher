@file:OptIn(ExperimentalMaterial3Api::class)

package com.petal.launcher.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.petal.launcher.LauncherViewModel
import com.petal.launcher.Overlay
import com.petal.launcher.data.*

private enum class Page { MAIN, HOME, DRAWER, GLANCE, DOCK, THEME, GESTURES, HIDDEN, BACKUP, ABOUT, LICENSES }

@Composable
fun SettingsScreen(vm: LauncherViewModel, s: LauncherState, apps: List<AppInfo>) {
    val ctx = LocalContext.current
    val st = s.settings
    var page by remember { mutableStateOf(Page.MAIN) }
    var confirmReset by remember { mutableStateOf(false) }

    BackHandler(enabled = page != Page.MAIN) { page = if (page == Page.LICENSES) Page.ABOUT else Page.MAIN }

    val back: () -> Unit = {
        if (page == Page.MAIN) vm.overlay = Overlay.NONE
        else page = if (page == Page.LICENSES) Page.ABOUT else Page.MAIN
    }

    when (page) {
        Page.MAIN -> SettingsPage("Launcher settings", back) {
            NavRow(Icons.Filled.Home, "Home screen", "Pages, grid, labels") { page = Page.HOME }
            NavRow(Icons.Filled.Apps, "App drawer", "Grid, labels, hidden apps") { page = Page.DRAWER }
            NavRow(Icons.Filled.WbSunny, "At a Glance", "Clock, date, battery") { page = Page.GLANCE }
            NavRow(Icons.Filled.Search, "Dock & search", "Search bar and engine") { page = Page.DOCK }
            NavRow(Icons.Filled.Palette, "Appearance", "Theme, colors, icon shape") { page = Page.THEME }
            NavRow(Icons.Filled.Gesture, "Gestures", "Swipe up / down, double tap") { page = Page.GESTURES }
            NavRow(Icons.Filled.VisibilityOff, "Hidden apps", "${s.hidden.size} hidden") { page = Page.HIDDEN }
            NavRow(Icons.Filled.Backup, "Backup & restore", "Export, import, reset") { page = Page.BACKUP }
            NavRow(Icons.Filled.Info, "About", "Version and licenses") { page = Page.ABOUT }
        }

        Page.HOME -> SettingsPage("Home screen", back) {
            ChoiceRow("Grid columns", st.homeColumns, listOf(4, 5), { "$it columns" }) { v ->
                vm.setSettings { it.copy(homeColumns = v) }
            }
            SwitchRow("Show labels", null, st.showHomeLabels) { v -> vm.setSettings { it.copy(showHomeLabels = v) } }
            ListItem(
                headlineContent = { Text("Pages") },
                supportingContent = { Text("${s.pages.size} of $MAX_PAGES  (removes the last page)") },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.removePage(s.pages.lastIndex) }, enabled = s.pages.size > 1) {
                            Icon(Icons.Filled.Remove, "Remove page")
                        }
                        IconButton(onClick = { vm.addPage() }, enabled = s.pages.size < MAX_PAGES) {
                            Icon(Icons.Filled.Add, "Add page")
                        }
                    }
                },
                colors = transparentItem()
            )
        }

        Page.DRAWER -> SettingsPage("App drawer", back) {
            ChoiceRow("Grid columns", st.drawerColumns, listOf(4, 5), { "$it columns" }) { v ->
                vm.setSettings { it.copy(drawerColumns = v) }
            }
            SwitchRow("Show labels", null, st.showDrawerLabels) { v -> vm.setSettings { it.copy(showDrawerLabels = v) } }
            NavRow(Icons.Filled.VisibilityOff, "Hidden apps", "${s.hidden.size} hidden") { page = Page.HIDDEN }
        }

        Page.GLANCE -> SettingsPage("At a Glance", back) {
            SwitchRow("Show At a Glance", null, st.showGlance) { v -> vm.setSettings { it.copy(showGlance = v) } }
            SwitchRow("24-hour clock", null, st.use24h) { v -> vm.setSettings { it.copy(use24h = v) } }
        }

        Page.DOCK -> SettingsPage("Dock & search", back) {
            SwitchRow("Show search bar", null, st.showQsb) { v -> vm.setSettings { it.copy(showQsb = v) } }
            ChoiceRow("Search engine", st.searchEngine, SearchEngine.entries, { it.label }) { v ->
                vm.setSettings { it.copy(searchEngine = v) }
            }
            Text(
                "Dock apps: long-press any app and choose Pin to Dock (max $DOCK_SLOTS).",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )
        }

        Page.THEME -> SettingsPage("Appearance", back) {
            ChoiceRow("Theme", st.themeMode, ThemeMode.entries, { it.label }) { v -> vm.setSettings { it.copy(themeMode = v) } }
            ChoiceRow(
                "Color palette", st.palette, Palette.entries,
                { if (it == Palette.DYNAMIC && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) "${it.label} (Android 12+)" else it.label }
            ) { v -> vm.setSettings { it.copy(palette = v) } }
            ChoiceRow("Workspace backdrop", st.backdrop, Backdrop.entries, { it.label }) { v ->
                vm.setSettings { it.copy(backdrop = v) }
            }
            Text("Icon shape", Modifier.padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconShape.entries.forEach { shape ->
                    val selected = shape == st.iconShape
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(52.dp).clip(shape.toShape())
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, shape.toShape())
                                .clickable { vm.setSettings { it.copy(iconShape = shape) } }
                        )
                        Text(shape.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        Page.GESTURES -> SettingsPage("Gestures", back) {
            ChoiceRow("Swipe up", st.swipeUp, GestureAction.entries, { it.label }) { v -> vm.setSettings { it.copy(swipeUp = v) } }
            ChoiceRow("Swipe down", st.swipeDown, GestureAction.entries, { it.label }) { v -> vm.setSettings { it.copy(swipeDown = v) } }
            ChoiceRow("Double tap", st.doubleTap, GestureAction.entries, { it.label }) { v -> vm.setSettings { it.copy(doubleTap = v) } }
        }

        Page.HIDDEN -> SettingsPage("Hidden apps", back, scrollable = false) {
            LazyColumn(Modifier.weight(1f)) {
                items(apps, key = { it.pkg }) { app ->
                    ListItem(
                        headlineContent = { Text(app.label) },
                        supportingContent = { Text(app.category.label) },
                        leadingContent = { AppIconImage(app.icon, st.iconShape, 40.dp) },
                        trailingContent = {
                            Checkbox(checked = app.pkg in s.hidden, onCheckedChange = { vm.toggleHidden(app.pkg) })
                        },
                        colors = transparentItem(),
                        modifier = Modifier.clickable { vm.toggleHidden(app.pkg) }
                    )
                }
            }
        }

        Page.BACKUP -> {
            val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) vm.exportBackup(uri)
            }
            val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) vm.importBackup(uri)
            }
            SettingsPage("Backup & restore", back) {
                NavRow(Icons.Filled.Upload, "Export backup", "Save layout and settings as .json") {
                    export.launch("petal_launcher_backup.json")
                }
                NavRow(Icons.Filled.Download, "Import backup", "Restore from a .json file") {
                    import.launch(arrayOf("*/*"))
                }
                NavRow(Icons.Filled.Restore, "Reset to defaults", "Clears layout, folders and settings") {
                    confirmReset = true
                }
            }
        }

        Page.ABOUT -> {
            val version = remember {
                runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
            }
            SettingsPage("About", back) {
                InfoRow("Version", version)
                InfoRow("Installed apps", apps.size.toString())
                InfoRow("Flowerpot rules loaded", vm.flowerpot.ruleCount.toString())
                NavRow(Icons.Filled.Description, "Open source licenses", null) { page = Page.LICENSES }
            }
        }

        Page.LICENSES -> SettingsPage("Open source licenses", back) {
            LICENSES.forEach { (name, license) ->
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = { Text(license) },
                    colors = transparentItem()
                )
            }
            Text(
                "Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0\n\n" +
                    "Petal Launcher is an independent project inspired by Lawnchair's design; no Lawnchair source code is used.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset launcher?") },
            text = { Text("All settings, home shortcuts, folders, dock items and hidden apps will be reset.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetToDefaults() }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}

private val LICENSES = listOf(
    "Kotlin & kotlinx.serialization" to "Apache License 2.0 — JetBrains",
    "AndroidX Core / Activity / Lifecycle" to "Apache License 2.0 — Google",
    "Jetpack Compose & Material 3" to "Apache License 2.0 — Google",
    "Material Icons Extended" to "Apache License 2.0 — Google",
    "AndroidX DataStore" to "Apache License 2.0 — Google"
)

@Composable
private fun transparentItem() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.systemBarsPadding()) {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
            if (scrollable) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = content)
            } else {
                Column(Modifier.weight(1f), content = content)
            }
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        colors = transparentItem(),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun InfoRow(title: String, value: String) {
    ListItem(headlineContent = { Text(title) }, trailingContent = { Text(value) }, colors = transparentItem())
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = transparentItem(),
        modifier = Modifier.clickable { onChange(!checked) }
    )
}

@Composable
private fun <T> ChoiceRow(title: String, current: T, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(label(current)) },
        colors = transparentItem(),
        modifier = Modifier.clickable { open = true }
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(option); open = false }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == current, onClick = { onSelect(option); open = false })
                            Text(label(option))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } }
        )
    }
}
