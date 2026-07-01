package com.tromshusky.callerlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == packageName) return@mapNotNull null
                AppInfo(
                    label = resolveInfo.loadLabel(pm).toString(),
                    packageName = pkg
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
        state.updateApps(apps)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                state.moveSelection(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                state.moveSelection(1)
                return true
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                onEnter()
                return true
            }
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                if (state.dialedNumber.isNotEmpty()) {
                    state.deleteDigit()
                    return true
                }
            }
            else -> {
                val char = event.getUnicodeChar(event.metaState).toChar()
                if (char.isDigit() || char == '+' || char == '*' || char == '#') {
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
        val intent = packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // App could not be launched.
        }
    }

    companion object {
        private const val REQUEST_CALL_PHONE = 1001
    }
}
