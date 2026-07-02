package com.tromshusky.callerlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.tromshusky.callerlauncher.ui.AppInfo
import com.tromshusky.callerlauncher.ui.LauncherScreen
import com.tromshusky.callerlauncher.ui.LauncherState
import com.tromshusky.callerlauncher.ui.theme.CallerLauncherTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val state = LauncherState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL_PHONE
            )
        }

        loadApps()

        setContent {
            CallerLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LauncherScreen(state)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    private fun loadApps() {
        // Loading icons for every app is relatively expensive; do it off the main thread.
        Thread {
            val apps = queryApps()
            runOnUiThread { state.updateApps(apps) }
        }.start()
    }

    /**
     * Lists launchable activities across all profiles. Apps in the primary profile are
     * "regular"; apps from any other profile (e.g. a managed work profile) are listed
     * afterwards with "[Work]" appended to their label.
     */
    private fun queryApps(): List<AppInfo> {
        val launcherApps = getSystemService(LauncherApps::class.java)
        val myUser = Process.myUserHandle()
        val regular = mutableListOf<AppInfo>()
        val work = mutableListOf<AppInfo>()

        for (profile in launcherApps.profiles) {
            val isWork = profile != myUser
            for (activity in launcherApps.getActivityList(null, profile)) {
                val pkg = activity.componentName.packageName
                if (pkg == packageName) continue

                val label = activity.label.toString()
                val icon = try {
                    activity.getBadgedIcon(0).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
                } catch (_: Exception) {
                    null
                }

                val app = AppInfo(
                    label = if (isWork) "$label [Work]" else label,
                    packageName = pkg,
                    componentName = activity.componentName,
                    user = profile,
                    isWork = isWork,
                    icon = icon
                )
                (if (isWork) work else regular).add(app)
            }
        }

        val byLabel = compareBy<AppInfo> { it.label.lowercase(Locale.getDefault()) }
        return regular.sortedWith(byLabel) + work.sortedWith(byLabel)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val numberActive = state.dialedNumber.isNotEmpty()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (numberActive) state.clearNumber() else state.moveSelection(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (numberActive) state.clearNumber() else state.moveSelection(1)
                return true
            }
            KeyEvent.KEYCODE_CALL,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                onEnter()
                return true
            }
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_FORWARD_DEL,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (numberActive) {
                    state.deleteDigit()
                    return true
                }
            }
            KeyEvent.KEYCODE_STAR -> {
                if (event.getRepeatCount() == 0) {
                    state.appendDigit('*')
                    return true
                } else {
                    state.turnIntoPlus()
                    return true
                }
            }
            else -> {
                val char = event.getUnicodeChar(event.metaState).toChar()
                if (char.isDigit() || char == '+' || char == '#') {
                    state.appendDigit(char)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun onEnter() {
        if (state.dialedNumber.isNotEmpty()) {
            dial(state.dialedNumber)
        } else {
            launchSelected()
        }
    }

    private fun dial(number: String) {
        val uri = Uri.fromParts("tel", number, null)
        val hasCallPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
            state.clearNumber()
        } catch (_: Exception) {
            // No dialer available; keep the number so the user can retry.
        }
    }

    private fun launchSelected() {
        val app = state.selectedApp() ?: return
        val launcherApps = getSystemService(LauncherApps::class.java)
        try {
            launcherApps.startMainActivity(app.componentName, app.user, null, null)
        } catch (_: Exception) {
            // App could not be launched.
        }
    }

    companion object {
        private const val REQUEST_CALL_PHONE = 1001
        private const val ICON_PX = 96
    }
}
