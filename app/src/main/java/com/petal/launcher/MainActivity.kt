package com.petal.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.launcher.data.Backdrop
import com.petal.launcher.ui.*
import com.petal.launcher.ui.theme.PetalTheme

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { LauncherRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        when {
            i.action == Intent.ACTION_APPLICATION_PREFERENCES -> vm.openSettings()
            i.data?.scheme == "petal" && i.data?.host == "settings" -> vm.openSettings()
            i.data?.scheme == "petal" && i.data?.host == "drawer" -> vm.openDrawer(false)
            i.action == Intent.ACTION_MAIN && i.hasCategory(Intent.CATEGORY_HOME) -> vm.goHome()
        }
    }
}

@Composable
private fun LauncherRoot(vm: LauncherViewModel) {
    val loaded by vm.state.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val state = loaded ?: return

    PetalTheme(state.settings) {
        val activity = LocalContext.current as ComponentActivity
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val lightBars = !dark && (vm.overlay != Overlay.NONE || state.settings.backdrop == Backdrop.SOLID)

        // Keep system bar icons readable
        DisposableEffect(lightBars) {
            val transparent = android.graphics.Color.TRANSPARENT
            val style = if (lightBars) SystemBarStyle.light(transparent, transparent) else SystemBarStyle.dark(transparent)
            activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose { }
        }

        BackHandler(enabled = vm.overlay != Overlay.NONE) { vm.overlay = Overlay.NONE }

        Box(Modifier.fillMaxSize()) {
            HomeScreen(vm, state, apps)

            AnimatedVisibility(
                visible = vm.overlay == Overlay.DRAWER,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut()
            ) { AppDrawer(vm, state, apps) }

            AnimatedVisibility(
                visible = vm.overlay == Overlay.SETTINGS,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut()
            ) { SettingsScreen(vm, state, apps) }

            vm.appSheet?.let { AppOptionsSheet(vm, state, it) }
            if (vm.showWorkspaceOptions) WorkspaceOptionsSheet(vm, state)
            if (vm.showCreateFolder) CreateFolderDialog(vm)

            val folder = vm.openFolderId?.let { id -> state.pages.flatten().firstOrNull { it.id == id } }
            if (folder != null) FolderDialog(vm, folder, apps)
        }
    }
}
