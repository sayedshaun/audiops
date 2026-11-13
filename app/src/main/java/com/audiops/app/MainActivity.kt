package com.audiops.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.audiops.app.ui.AudiopsApp
import com.audiops.app.ui.isDarkTheme
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Android host for the shared [AudiopsApp]. */
class MainActivity : ComponentActivity() {
    private lateinit var viewModel: AudiopsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Application-scoped, so jobs keep running when this Activity is destroyed.
        viewModel = (application as AudiopsApplication).viewModel(savedInstanceState?.getBundle(KEY_VIEW_MODEL))
        if (savedInstanceState == null) handleIntent(intent)
        askForNotificationsOnFirstJob()
        // Hold the splash screen until settings load, so the first frame uses the chosen theme.
        splash.setKeepOnScreenCondition { viewModel.settings.value == null }
        enableEdgeToEdge()

        setContent {
            val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: UserSettings()
            SystemBarsFor(settings.isDarkTheme())
            AudiopsApp(viewModel, appVersion = BuildConfig.VERSION_NAME)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(KEY_VIEW_MODEL, (application as AudiopsApplication).saveViewModelState())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A tap on a job notification opens the Activity tab. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(JobNotifications.EXTRA_SHOW_ACTIVITY, false) != true) return
        viewModel.back()
        viewModel.selectTab(Tab.ACTIVITY)
    }

    /**
     * Android 13+ needs permission to show the progress and "finished" notifications. Ask
     * once, when the user starts their first job, so the request has an obvious reason.
     */
    private fun askForNotificationsOnFirstJob() {
        if (Build.VERSION.SDK_INT < 33) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED || prefs.getBoolean(KEY_ASKED, false)) return
        lifecycleScope.launch {
            viewModel.state.filter { it.activeJobs.isNotEmpty() }.first()
            prefs.edit().putBoolean(KEY_ASKED, true).apply()
            // The answer needs no handling: notifications simply show or not.
            requestPermissions(arrayOf(permission), 0)
        }
    }

    /** Status and navigation bar icons follow the in-app theme, not only the system theme. */
    @Composable
    private fun SystemBarsFor(darkTheme: Boolean) {
        DisposableEffect(darkTheme) {
            val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose { }
        }
    }

    private companion object {
        const val KEY_VIEW_MODEL = "view_model"
        const val PREFS = "notifications"
        const val KEY_ASKED = "permission_asked"
    }
}
