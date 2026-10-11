package com.example.yoyo

import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yoyo.audio.MediaPlayerAudioEngine
import com.example.yoyo.ui.TrackerShell
import com.example.yoyo.ui.YoYoTheme
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val controller = AppController(
        scope = viewModelScope,
        audio = MediaPlayerAudioEngine(application),
        store = SharedPreferencesStateStore(application),
    )

    init {
        viewModelScope.launch { controller.initialize() }
    }

    override fun onCleared() {
        controller.dispose()
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val controller = viewModel.controller
        setContent {
            val keepScreenOn = controller.keepScreenOn
            LaunchedEffect(keepScreenOn) {
                if (keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            val fullscreen = controller.fullscreen
            LaunchedEffect(fullscreen) { applySystemBars(fullscreen) }
            YoYoTheme {
                TrackerShell(controller)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs and the keyboard can bring the bars back; re-hide on return.
        if (hasFocus) applySystemBars(viewModel.controller.fullscreen)
    }

    /** Immersive-sticky when fullscreen, otherwise plain edge-to-edge. */
    private fun applySystemBars(fullscreen: Boolean) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true
        insetsController.isAppearanceLightNavigationBars = true
        if (fullscreen) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
