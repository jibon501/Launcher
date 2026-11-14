package com.petal.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import com.petal.launcher.data.SearchEngine

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

fun safeStart(ctx: Context, intent: Intent, error: String = "Nothing can handle this action") {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        toast(ctx, error)
    }
}

fun launchApp(ctx: Context, pkg: String) {
    val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
    if (intent != null) safeStart(ctx, intent, "Cannot open this app") else toast(ctx, "App not found")
}

fun openAppInfo(ctx: Context, pkg: String) =
    safeStart(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

fun uninstallApp(ctx: Context, pkg: String) =
    safeStart(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))

fun openCalendar(ctx: Context) =
    safeStart(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR), "No calendar app found")

fun openClock(ctx: Context) =
    safeStart(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS), "No clock app found")

fun openWallpaperPicker(ctx: Context) =
    safeStart(ctx, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Select wallpaper"))

fun openWebSearch(ctx: Context, engine: SearchEngine, query: String) {
    if (query.isBlank()) return
    safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(engine.url + Uri.encode(query.trim()))), "No browser found")
}

/** Uses the hidden StatusBarManager API (needs EXPAND_STATUS_BAR). May not work on every device. */
fun expandNotificationPanel(ctx: Context) {
    try {
        val sb = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
    } catch (_: Throwable) {
        toast(ctx, "Notification panel gesture is not supported on this device")
    }
}
