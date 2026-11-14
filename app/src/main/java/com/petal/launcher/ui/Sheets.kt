@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.petal.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.petal.launcher.*
import com.petal.launcher.data.*

@Composable
private fun SheetAction(icon: ImageVector, text: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** Long-press menu for any app icon. */
@Composable
fun AppOptionsSheet(vm: LauncherViewModel, s: LauncherState, app: AppInfo) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val onHome = s.pages.any { page -> page.any { it.pkg == app.pkg } }
    val inDock = app.pkg in s.dock
    val hidden = app.pkg in s.hidden
    fun close() { vm.appSheet = null }

    ModalBottomSheet(onDismissRequest = { close() }, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 16.dp)) {
            ListItem(
                headlineContent = { Text(app.label, style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(app.pkg) },
                leadingContent = { AppIconImage(app.icon, s.settings.iconShape, 44.dp) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            HorizontalDivider()
            SheetAction(Icons.Filled.PushPin, if (onHome) "Unpin from Home" else "Pin to Home") {
                if (onHome) vm.unpinFromHome(app.pkg) else vm.pinToHome(app.pkg); close()
            }
            SheetAction(Icons.Filled.Dock, if (inDock) "Remove from Dock" else "Pin to Dock") {
                vm.toggleDock(app.pkg); close()
            }
            SheetAction(
                if (hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                if (hidden) "Unhide from App Drawer" else "Hide from App Drawer"
            ) { vm.toggleHidden(app.pkg); close() }
            SheetAction(Icons.Filled.Info, "App info") { openAppInfo(ctx, app.pkg); close() }
            if (!app.isSystem) {
                SheetAction(Icons.Filled.Delete, "Uninstall") { uninstallApp(ctx, app.pkg); close() }
            }
        }
    }
}

/** Long-press on empty workspace. */
@Composable
fun WorkspaceOptionsSheet(vm: LauncherViewModel, s: LauncherState) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun close() { vm.showWorkspaceOptions = false }

    ModalBottomSheet(onDismissRequest = { close() }, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 16.dp)) {
            SheetAction(Icons.Filled.Settings, "Launcher settings") { vm.openSettings() }
            SheetAction(Icons.Filled.CreateNewFolder, "Create folder") { close(); vm.showCreateFolder = true }
            SheetAction(Icons.Filled.Wallpaper, "System wallpaper") { openWallpaperPicker(ctx); close() }
            if (s.pages.size < MAX_PAGES) {
                SheetAction(Icons.Filled.Add, "Add page") { vm.addPage(); close() }
            }
            if (s.pages.size > 1) {
                SheetAction(Icons.Filled.Remove, "Remove this page (${vm.currentPage + 1}/${s.pages.size})") {
                    vm.removePage(vm.currentPage); close()
                }
            }
        }
    }
}

@Composable
fun CreateFolderDialog(vm: LauncherViewModel) {
    var name by remember { mutableStateOf("Folder") }
    AlertDialog(
        onDismissRequest = { vm.showCreateFolder = false },
        title = { Text("New folder") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Folder name") }) },
        confirmButton = { TextButton(onClick = { vm.showCreateFolder = false; vm.createFolder(name) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = { vm.showCreateFolder = false }) { Text("Cancel") } }
    )
}

@Composable
fun FolderDialog(vm: LauncherViewModel, folder: HomeItem, apps: List<AppInfo>) {
    val ctx = LocalContext.current
    val appMap = remember(apps) { apps.associateBy { it.pkg } }
    val folderApps = folder.folderApps.mapNotNull { appMap[it] }
    var name by remember(folder.id) { mutableStateOf(folder.folderName.orEmpty()) }
    var showPicker by remember { mutableStateOf(false) }
    val onSurface = MaterialTheme.colorScheme.onSurface

    Dialog(onDismissRequest = { vm.openFolderId = null }) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; vm.renameFolder(folder.id, it) },
                    singleLine = true, label = { Text("Folder name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                if (folderApps.isEmpty()) {
                    Text(
                        "This folder is empty. Tap \"Add apps\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(folderApps, key = { it.pkg }) { app ->
                            AppCell(
                                app, IconShape.CIRCLE, true, onSurface, false,
                                onClick = { vm.openFolderId = null; launchApp(ctx, app.pkg) },
                                onLongClick = { vm.toggleFolderApp(folder.id, app.pkg) } // remove from folder
                            )
                        }
                    }
                    Text(
                        "Long-press an app to remove it from the folder",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { showPicker = true }) { Text("Add apps") }
                    TextButton(onClick = { vm.deleteFolder(folder.id) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { vm.openFolderId = null }) { Text("Done") }
                }
            }
        }
    }

    if (showPicker) {
        Dialog(onDismissRequest = { showPicker = false }) {
            Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
                Column(Modifier.padding(vertical = 16.dp)) {
                    Text("Add apps", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(apps, key = { it.pkg }) { app ->
                            ListItem(
                                headlineContent = { Text(app.label) },
                                leadingContent = { AppIconImage(app.icon, IconShape.CIRCLE, 36.dp) },
                                trailingContent = {
                                    Checkbox(
                                        checked = app.pkg in folder.folderApps,
                                        onCheckedChange = { vm.toggleFolderApp(folder.id, app.pkg) }
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { vm.toggleFolderApp(folder.id, app.pkg) }
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showPicker = false }) { Text("Done") }
                    }
                }
            }
        }
    }
}
