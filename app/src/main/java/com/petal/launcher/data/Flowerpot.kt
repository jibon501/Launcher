package com.petal.launcher.data

import android.content.Context
import android.content.pm.ApplicationInfo

enum class AppCategory(val label: String) {
    COMMUNICATION("Communication"), PRODUCTIVITY("Productivity"), TOOLS("Tools"), SOCIAL("Social"),
    ENTERTAINMENT("Entertainment"), MUSIC("Music"), PHOTOGRAPHY("Photography"), GAMES("Games"),
    NEWS("News"), FINANCE("Finance"), SHOPPING("Shopping"), TRAVEL("Travel"), OTHER("Other");

    companion object {
        fun fromKey(key: String): AppCategory? = entries.firstOrNull { it.label.equals(key.trim(), true) }
    }
}

/**
 * Flowerpot category engine.
 * Order: 1) rule files in assets/flowerpot/*  2) ApplicationInfo.category  3) package-name heuristics.
 */
class Flowerpot(private val ctx: Context) {
    private val rules = mutableListOf<Pair<String, AppCategory>>()
    val ruleCount: Int get() = rules.size

    private val heuristics: List<Pair<AppCategory, List<String>>> = listOf(
        AppCategory.GAMES to listOf("game", "puzzle"),
        AppCategory.MUSIC to listOf("music", "audio", "podcast", "radio"),
        AppCategory.PHOTOGRAPHY to listOf("camera", "gallery", "photo"),
        AppCategory.FINANCE to listOf("bank", "wallet", "finance", "money", "bkash", "nagad"),
        AppCategory.SHOPPING to listOf("shop", "store", "mart"),
        AppCategory.TRAVEL to listOf("map", "travel", "taxi", "ride", "hotel", "flight", "navigation"),
        AppCategory.NEWS to listOf("news"),
        AppCategory.COMMUNICATION to listOf("mail", "messag", "sms", "dialer", "contacts", "chat", "meet"),
        AppCategory.PRODUCTIVITY to listOf("office", "docs", "note", "calendar", "todo", "task", "drive"),
        AppCategory.TOOLS to listOf("calculator", "clock", "settings", "filemanager", "files", "browser", "vpn", "scanner", "clean"),
        AppCategory.ENTERTAINMENT to listOf("video", "movie", "stream", "player")
    )

    init { loadRules() }

    private fun loadRules() {
        val files = runCatching { ctx.assets.list("flowerpot") }.getOrNull().orEmpty().sorted()
        for (file in files) {
            runCatching {
                ctx.assets.open("flowerpot/$file").bufferedReader().useLines { lines ->
                    lines.map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .forEach { line ->
                            val i = line.indexOf(':')
                            if (i > 0) {
                                val cat = AppCategory.fromKey(line.substring(0, i))
                                val prefix = line.substring(i + 1).trim()
                                if (cat != null && prefix.isNotEmpty()) rules += prefix to cat
                            }
                        }
                }
            }
        }
    }

    fun classify(pkg: String, androidCategory: Int): AppCategory {
        rules.firstOrNull { (prefix, _) -> pkg.startsWith(prefix) }?.let { return it.second }
        when (androidCategory) {
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
            ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.MUSIC
            ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.ENTERTAINMENT
            ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.PHOTOGRAPHY
            ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_NEWS -> return AppCategory.NEWS
            ApplicationInfo.CATEGORY_MAPS -> return AppCategory.TRAVEL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.PRODUCTIVITY
        }
        val lower = pkg.lowercase()
        return heuristics.firstOrNull { (_, keys) -> keys.any { lower.contains(it) } }?.first
            ?: AppCategory.OTHER
    }
}
