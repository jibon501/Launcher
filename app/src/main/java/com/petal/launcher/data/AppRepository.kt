package com.petal.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class AppInfo(
    val pkg: String,
    val label: String,
    val isSystem: Boolean,
    val category: AppCategory,
    val icon: ImageBitmap
)

/** Loads launchable apps (label, icon, category) through PackageManager. */
class AppRepository(private val ctx: Context, private val flowerpot: Flowerpot) {
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    suspend fun load() = withContext(Dispatchers.Default) {
        val pm = ctx.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(query, 0)
        _apps.value = resolved.asSequence().mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            if (ai.packageName == ctx.packageName) return@mapNotNull null
            val appInfo = ai.applicationInfo ?: return@mapNotNull null
            AppInfo(
                pkg = ai.packageName,
                label = ri.loadLabel(pm).toString(),
                isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                category = flowerpot.classify(ai.packageName, appInfo.category),
                icon = ri.loadIcon(pm).toIconBitmap()
            )
        }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }.toList()
    }

    private fun Drawable.toIconBitmap(size: Int = 160): ImageBitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (this is AdaptiveIconDrawable) {
            // Adaptive icons: visible area is the centre 72/108 -> draw oversized and crop
            val full = (size * 1.5f).toInt()
            val off = -((full - size) / 2)
            setBounds(off, off, off + full, off + full)
        } else {
            val pad = (size * 0.08f).toInt()
            setBounds(pad, pad, size - pad, size - pad)
        }
        draw(canvas)
        return bmp.asImageBitmap()
    }
}
