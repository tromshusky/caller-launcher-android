package com.tromshusky.callerlauncher.ui

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

data class AppInfo(
    val label: String,
    val packageName: String,
    val componentName: ComponentName,
    val user: UserHandle,
    val isWork: Boolean,
    val icon: ImageBitmap?
)

/**
 * Holds the observable UI state for the launcher. Input events from the hardware
 * keyboard are handled by the Activity, which mutates this state; Compose reads it.
 */
class LauncherState {

    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set

    var selectedIndex by mutableStateOf(0)
        private set

    var dialedNumber by mutableStateOf("")
        private set

    var favoriteApps by mutableStateOf<Set<String>>(emptySet())
        private set

    var hiddenApps by mutableStateOf<Set<String>>(emptySet())
        private set

    fun updateApps(newApps: List<AppInfo>) {
        apps = newApps
        selectedIndex = selectedIndex.coerceIn(0, (newApps.size - 1).coerceAtLeast(0))
    }

    fun setFavorites(favorites: Set<String>) {
        favoriteApps = favorites
    }

    fun setHiddens(hidden: Set<String>) {
        hiddenApps = hidden
    }

    fun toggleFavorite(packageName: String) {
        favoriteApps = if (packageName in favoriteApps) {
            favoriteApps - packageName
        } else {
            favoriteApps + packageName
        }
    }

    fun toggleHidden(packageName: String) {
        hiddenApps = if (packageName in hiddenApps) {
            hiddenApps - packageName
        } else {
            hiddenApps + packageName
        }
    }

    fun isFavorite(packageName: String): Boolean = packageName in favoriteApps

    fun isHidden(packageName: String): Boolean = packageName in hiddenApps

    fun getFilteredAndSortedApps(): List<AppInfo> {
        val visible = apps.filter { it.packageName !in hiddenApps }
        val favorites = visible.filter { it.packageName in favoriteApps }
        val regular = visible.filter { it.packageName !in favoriteApps }
        return favorites + regular
    }

    fun moveSelection(delta: Int) {
        if (apps.isEmpty()) return
        selectedIndex = (selectedIndex + delta).coerceIn(0, apps.size - 1)
    }

    fun selectIndex(index: Int) {
        if (apps.isEmpty()) return
        selectedIndex = index.coerceIn(0, apps.size - 1)
    }

    fun findSelect(char: Char) {
        if (apps.isEmpty()) return
        val idx = apps.indexOfFirst { it.label.startsWith(char.toString(), ignoreCase = true) }
        if (idx >= 0) selectedIndex = idx
    }

    fun appendDigit(char: Char) {
        dialedNumber += char
    }

    fun deleteDigit() {
        if (dialedNumber.isNotEmpty()) {
            dialedNumber = dialedNumber.dropLast(1)
        }
    }

    fun clearNumber() {
        dialedNumber = ""
    }

    fun turnIntoPlus() {
        if (dialedNumber.last() == '*') {
            dialedNumber = dialedNumber.dropLast(1) + '+'
        }
    }

    fun selectedApp(): AppInfo? = apps.getOrNull(selectedIndex)
}
