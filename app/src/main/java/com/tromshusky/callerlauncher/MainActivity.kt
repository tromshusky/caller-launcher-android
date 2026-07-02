package com.tromshusky.callerlauncher

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.provider.Telephony
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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

        setContent {
            CallerLauncherTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    launchSelected()
                                },
                                onDoubleTap = {
                                    showAppInfo()
                                },
                                onLongPress = {
                                    uninstallApp()
                                }
                            )
                        },
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
            KeyEvent.KEYCODE_F7 -> {
                if (numberActive) {
                    openSms(state.dialedNumber)
                } else {
                    openSms()
                }
                return true
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

    private fun openSms(number: String? = null) {
        if (!number.isNullOrBlank()) {
            val uri = Uri.fromParts("smsto", number, null)
            val intent = Intent(Intent.ACTION_SENDTO, uri)
            try {
                startActivity(intent)
                state.clearNumber()
            } catch (_: Exception) {
                // No messaging app available; keep the number so the user can retry.
            }
        } else {
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
            if (defaultSmsPackage == null) return
            val intent = if (defaultSmsPackage == "gwin.com.firefox") {
                // TTFone sms app
                Intent().apply {
                    component = ComponentName("gwin.com.firefox", "gwin.com.firefox.mms.MessageActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                packageManager.getLaunchIntentForPackage(defaultSmsPackage)
            }
            if (intent == null) return
            try {
                startActivity(intent)
            } catch (_: Exception) {
                // No SMS app or cannot launch
            }
        }
    }

    private fun showAppInfo() {
        val app = state.selectedApp() ?: return
        val pkg = app.packageName
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", pkg, null)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // fallback: try LauncherApps if available (for managed profiles)
            val launcherApps = getSystemService(LauncherApps::class.java)
            try {
                // launcherApps may provide profile-aware APIs; try to open details via launcher if available
                launcherApps?.startMainActivity(app.componentName, app.user, null, null)
            } catch (_: Exception) {
                // cannot open app info
            }
        }
    }

    private fun uninstallApp() {
        val app = state.selectedApp() ?: return
        val pkg = app.packageName
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg")).apply {
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // uninstall may be restricted (system app or work profile); handle gracefully
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
