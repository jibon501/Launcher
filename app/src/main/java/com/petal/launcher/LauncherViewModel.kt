package com.petal.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.petal.launcher.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Overlay { NONE, DRAWER, SETTINGS }

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val store = LauncherStore(ctx)
    val flowerpot = Flowerpot(ctx)
    private val repo = AppRepository(ctx, flowerpot)

    val state: StateFlow<LauncherState?> = store.stateFlow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val apps: StateFlow<List<AppInfo>> = repo.apps

    // ---- UI state (Compose snapshot state) ----
    var overlay by mutableStateOf(Overlay.NONE)
    var drawerFocusSearch by mutableStateOf(false)
    var appSheet by mutableStateOf<AppInfo?>(null)
    var showWorkspaceOptions by mutableStateOf(false)
    var showCreateFolder by mutableStateOf(false)
    var openFolderId by mutableStateOf<String?>(null)
    var currentPage by mutableIntStateOf(0)
    var homeTick by mutableIntStateOf(0)

    private var refreshJob: Job? = null

    // Live updates when apps are installed / removed / updated
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_PACKAGE_REMOVED && i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            refresh(300)
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh(0)

        // First run: seed dock and home with common apps
        viewModelScope.launch {
            combine(state.filterNotNull(), apps) { s, a -> s to a }.collect { (s, a) ->
                if (!s.initialized && a.isNotEmpty()) update { seed(it, a) }
            }
        }
        // Remove shortcuts of uninstalled apps
        viewModelScope.launch {
            apps.filter { it.isNotEmpty() }.collect { list ->
                val installed = list.map { it.pkg }.toSet()
                update { prune(it, installed) }
            }
        }
    }

    private fun refresh(delayMs: Long) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            repo.load()
        }
    }

    override fun onCleared() {
        runCatching { ctx.unregisterReceiver(receiver) }
    }

    // ---- State edits ----
    fun update(t: (LauncherState) -> LauncherState) {
        viewModelScope.launch { store.edit(t) }
    }

    fun setSettings(t: (LauncherSettings) -> LauncherSettings) = update { it.copy(settings = t(it.settings)) }

    private fun toastMain(msg: String) {
        viewModelScope.launch(Dispatchers.Main) { toast(ctx, msg) }
    }

    private fun seed(s: LauncherState, apps: List<AppInfo>): LauncherState {
        if (s.initialized) return s
        val pm = ctx.packageManager
        val installed = apps.map { it.pkg }.toSet()
        fun res(i: Intent) = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it in installed }
        fun cat(c: String) = res(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, c))

        val dock = listOfNotNull(
            res(Intent(Intent.ACTION_DIAL)),
            cat(Intent.CATEGORY_APP_MESSAGING),
            cat(Intent.CATEGORY_APP_BROWSER),
            res(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        ).distinct()
        val home = listOfNotNull(
            res(Intent(Settings.ACTION_SETTINGS)),
            cat(Intent.CATEGORY_APP_GALLERY), cat(Intent.CATEGORY_APP_CALENDAR),
            cat(Intent.CATEGORY_APP_CALCULATOR), cat(Intent.CATEGORY_APP_MAPS),
            cat(Intent.CATEGORY_APP_MUSIC), cat(Intent.CATEGORY_APP_EMAIL)
        ).distinct().filter { it !in dock }
        return s.copy(
            initialized = true,
            dock = dock,
            pages = listOf(home.map { HomeItem(id = newId(), pkg = it) }) + s.pages.drop(1)
        )
    }

    private fun prune(s: LauncherState, installed: Set<String>): LauncherState = s.copy(
        pages = s.pages.map { page ->
            page.mapNotNull { item ->
                when {
                    item.isFolder -> item.copy(folderApps = item.folderApps.filter { a -> a in installed })
                    item.pkg in installed -> item
                    else -> null
                }
            }
        },
        dock = s.dock.filter { it in installed },
        hidden = s.hidden.filter { it in installed }.toSet()
    )

    // ---- Home / dock / hidden ----
    fun pinToHome(pkg: String) {
        val s = state.value ?: return
        val page = currentPage
        if (s.pinToHome(pkg, page) == null) toast(ctx, "Home screen is full")
        else update { it.pinToHome(pkg, page) ?: it }
    }

    fun unpinFromHome(pkg: String) = update { s -> s.mapItems { if (!it.isFolder && it.pkg == pkg) null else it } }

    fun toggleDock(pkg: String) {
        val s = state.value ?: return
        if (pkg !in s.dock && s.dock.size >= DOCK_SLOTS) {
            toast(ctx, "Dock is full ($DOCK_SLOTS apps)"); return
        }
        update { st -> if (pkg in st.dock) st.copy(dock = st.dock - pkg) else st.copy(dock = st.dock + pkg) }
    }

    fun toggleHidden(pkg: String) =
        update { it.copy(hidden = if (pkg in it.hidden) it.hidden - pkg else it.hidden + pkg) }

    // ---- Pages ----
    fun addPage() {
        val s = state.value ?: return
        if (s.pages.size >= MAX_PAGES) { toast(ctx, "Maximum $MAX_PAGES pages"); return }
        update { it.copy(pages = it.pages + listOf(emptyList<HomeItem>())) }
    }

    fun removePage(index: Int) = update {
        if (it.pages.size <= 1) it else it.copy(pages = it.pages.filterIndexed { i, _ -> i != index })
    }

    // ---- Folders ----
    fun createFolder(name: String) {
        val s = state.value ?: return
        val page = currentPage.coerceIn(0, s.pages.lastIndex)
        val target = (listOf(page) + s.pages.indices).firstOrNull { s.pages[it].size < HOME_CAPACITY }
        if (target == null) { toast(ctx, "Home screen is full"); return }
        val id = newId()
        update { st ->
            st.copy(pages = st.pages.mapIndexed { i, p ->
                if (i == target) p + HomeItem(id = id, folderName = name.ifBlank { "Folder" }) else p
            })
        }
        openFolderId = id
    }

    private fun updateFolder(id: String, t: (HomeItem) -> HomeItem) =
        update { s -> s.mapItems { if (it.id == id) t(it) else it } }

    fun renameFolder(id: String, name: String) = updateFolder(id) { it.copy(folderName = name) }

    fun toggleFolderApp(id: String, pkg: String) = updateFolder(id) {
        it.copy(folderApps = if (pkg in it.folderApps) it.folderApps - pkg else it.folderApps + pkg)
    }

    fun deleteFolder(id: String) {
        openFolderId = null
        update { s -> s.mapItems { if (it.id == id) null else it } }
    }

    fun setReminder(text: String) = update { it.copy(reminder = text.trim()) }

    // ---- Navigation ----
    fun openDrawer(focusSearch: Boolean) {
        drawerFocusSearch = focusSearch
        overlay = Overlay.DRAWER
    }

    fun openSettings() {
        showWorkspaceOptions = false
        overlay = Overlay.SETTINGS
    }

    fun goHome() {
        val wasHome = overlay == Overlay.NONE
        overlay = Overlay.NONE
        appSheet = null
        showWorkspaceOptions = false
        openFolderId = null
        if (wasHome) homeTick++
    }

    // ---- Backup / restore ----
    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val text = store.encodeBackup(state.value ?: LauncherState())
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            }.onSuccess { toastMain("Backup saved") }
                .onFailure { toastMain("Backup failed: ${it.message}") }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                store.replace(store.parseBackup(text).copy(initialized = true))
            }.onSuccess { toastMain("Backup restored") }
                .onFailure { toastMain("Invalid backup file") }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            store.replace(LauncherState())
            toastMain("Launcher reset")
        }
    }
}
