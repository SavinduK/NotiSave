package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build

data class SelectableApp(
    val packageName: String,
    val appName: String,
    val isSelected: Boolean
)

object AppFilterManager {
    private const val PREFS_NAME = "notify_app_filter_prefs"
    private const val KEY_FILTER_ENABLED = "key_filter_enabled"
    private const val KEY_SELECTED_PACKAGES = "key_selected_packages"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * If false, notifications from all apps are captured (default).
     * If true, only notifications from apps in [getSelectedPackages] are captured.
     */
    fun isFilterEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FILTER_ENABLED, false)
    }

    fun setFilterEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FILTER_ENABLED, enabled).apply()
    }

    fun getSelectedPackages(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_SELECTED_PACKAGES, null) ?: emptySet()
    }

    fun setSelectedPackages(context: Context, packages: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_SELECTED_PACKAGES, packages).apply()
    }

    fun togglePackageSelection(context: Context, packageName: String, selected: Boolean) {
        val current = getSelectedPackages(context).toMutableSet()
        if (selected) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        setSelectedPackages(context, current)
    }

    /**
     * Checks if a notification from [packageName] should be saved.
     * Returns true if filter is disabled, or if the package is in the selected set.
     */
    fun isAppAllowed(context: Context, packageName: String): Boolean {
        if (!isFilterEnabled(context)) {
            return true
        }
        val selected = getSelectedPackages(context)
        return selected.contains(packageName)
    }

    /**
     * Resolves a list of selectable apps from installed launchable apps and
     * previously recorded notification packages.
     */
    fun getSelectableApps(
        context: Context,
        additionalPackages: Set<String> = emptySet()
    ): List<SelectableApp> {
        val pm = context.packageManager
        val selectedPackages = getSelectedPackages(context)
        val appMap = mutableMapOf<String, String>() // packageName -> appName

        // 1. Discover launcher activities
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }

            for (info in resolveInfos) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg == context.packageName) continue // Don't list our own app
                val label = info.loadLabel(pm).toString().ifBlank { pkg }
                appMap[pkg] = label
            }
        } catch (e: Exception) {
            // Ignored
        }

        // 2. Add any additional packages recorded from notifications
        for (pkg in additionalPackages) {
            if (pkg == context.packageName) continue
            if (!appMap.containsKey(pkg)) {
                val label = try {
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(pkg, 0)
                    }
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                }
                appMap[pkg] = label
            }
        }

        // Fallback default list if no external apps can be queried in sandbox
        if (appMap.isEmpty()) {
            val sampleApps = listOf(
                "com.slack" to "Slack",
                "com.whatsapp" to "WhatsApp",
                "com.google.android.gm" to "Gmail",
                "com.github.android" to "GitHub",
                "com.telegram.messenger" to "Telegram",
                "com.twitter.android" to "X (Twitter)",
                "com.google.android.apps.messaging" to "Messages",
                "com.discord" to "Discord"
            )
            for ((pkg, name) in sampleApps) {
                appMap[pkg] = name
            }
        }

        return appMap.map { (pkg, name) ->
            SelectableApp(
                packageName = pkg,
                appName = name,
                isSelected = selectedPackages.contains(pkg)
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
    }
}
