package com.petal.launcher.data

import kotlinx.serialization.Serializable
import java.util.UUID

const val HOME_CAPACITY = 20 // 4 cols x 5 rows  OR  5 cols x 4 rows
const val MAX_PAGES = 5
const val DOCK_SLOTS = 4

@Serializable enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

@Serializable enum class Palette(val label: String) {
    DYNAMIC("Material You (dynamic)"), BLUE("Ocean Blue"), GREEN("Forest Green"),
    PURPLE("Royal Purple"), ORANGE("Sunset Orange"), ROSE("Rose Pink")
}

@Serializable enum class Backdrop(val label: String) {
    NONE("None (clean wallpaper)"), SCRIM("Soft scrim"), GRADIENT("Gradient"), SOLID("Solid surface")
}

@Serializable enum class IconShape(val label: String) {
    CIRCLE("Circle"), SQUIRCLE("Squircle"), ROUNDED_SQUARE("Rounded square"), TEARDROP("Teardrop")
}

@Serializable enum class GestureAction(val label: String) {
    OPEN_DRAWER("Open app drawer"), OPEN_SEARCH("Open search"),
    EXPAND_NOTIFICATIONS("Expand notifications"), OPEN_SETTINGS("Open launcher settings"), NONE("None")
}

@Serializable enum class SearchEngine(val label: String, val url: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BRAVE("Brave Search", "https://search.brave.com/search?q="),
    KAGI("Kagi", "https://kagi.com/search?q="),
    STARTPAGE("Startpage", "https://www.startpage.com/do/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    YANDEX("Yandex", "https://yandex.com/search/?text=")
}

@Serializable
data class LauncherSettings(
    val homeColumns: Int = 4,
    val showHomeLabels: Boolean = true,
    val drawerColumns: Int = 4,
    val showDrawerLabels: Boolean = true,
    val showGlance: Boolean = true,
    val use24h: Boolean = false,
    val showQsb: Boolean = true,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val palette: Palette = Palette.DYNAMIC,
    val backdrop: Backdrop = Backdrop.SCRIM,
    val iconShape: IconShape = IconShape.CIRCLE,
    val swipeUp: GestureAction = GestureAction.OPEN_DRAWER,
    val swipeDown: GestureAction = GestureAction.EXPAND_NOTIFICATIONS,
    val doubleTap: GestureAction = GestureAction.NONE
)

/** A home cell: either an app shortcut (pkg != null) or a folder (folderName != null). */
@Serializable
data class HomeItem(
    val id: String,
    val pkg: String? = null,
    val folderName: String? = null,
    val folderApps: List<String> = emptyList()
) {
    val isFolder: Boolean get() = folderName != null
}

@Serializable
data class LauncherState(
    val initialized: Boolean = false,
    val settings: LauncherSettings = LauncherSettings(),
    val pages: List<List<HomeItem>> = listOf(emptyList()),
    val dock: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val reminder: String = ""
)

fun newId(): String = UUID.randomUUID().toString()

/** Clamp imported / loaded data to valid ranges. */
fun LauncherState.sanitized(): LauncherState = copy(
    settings = settings.copy(
        homeColumns = settings.homeColumns.coerceIn(4, 5),
        drawerColumns = settings.drawerColumns.coerceIn(4, 5)
    ),
    pages = pages.take(MAX_PAGES).map { it.take(HOME_CAPACITY) }.ifEmpty { listOf(emptyList()) },
    dock = dock.distinct().take(DOCK_SLOTS)
)

fun LauncherState.mapItems(f: (HomeItem) -> HomeItem?): LauncherState =
    copy(pages = pages.map { page -> page.mapNotNull(f) })

/** Pin an app to home. Returns null if every page is full. */
fun LauncherState.pinToHome(pkg: String, preferredPage: Int): LauncherState? {
    if (pages.any { page -> page.any { it.pkg == pkg } }) return this
    val first = preferredPage.coerceIn(0, pages.lastIndex)
    val order = listOf(first) + pages.indices.filter { it != first }
    val target = order.firstOrNull { pages[it].size < HOME_CAPACITY } ?: return null
    return copy(pages = pages.mapIndexed { i, page ->
        if (i == target) page + HomeItem(id = newId(), pkg = pkg) else page
    })
}
