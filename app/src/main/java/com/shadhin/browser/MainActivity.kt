package com.shadhin.browser

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.shadhin.browser.ui.BrowserScreen
import com.shadhin.browser.ui.theme.ShadhinTheme
import com.shadhin.browser.viewmodel.BrowserUiIntent
import com.shadhin.browser.viewmodel.BrowserViewModel
import com.shadhin.browser.viewmodel.FullScreenHost

class MainActivity : ComponentActivity(), FullScreenHost {

    private val viewModel: BrowserViewModel by viewModels()

    private var customVideoView: View? = null
    private var customVideoViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var fullscreenContainer: FrameLayout
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        originalOrientation = requestedOrientation

        // Full-screen video container attached directly to decor view (outside Compose)
        fullscreenContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        val decorView = window.decorView as ViewGroup
        decorView.addView(fullscreenContainer)

        viewModel.fullScreenHost = this

        // Back button interception: exit full-screen first
        onBackPressedDispatcher.addCallback(this) {
            if (customVideoView != null) {
                onExitFullScreen()
            } else {
                viewModel.onIntent(BrowserUiIntent.GoBack)
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            ShadhinTheme(themeMode = uiState.themeMode) {
                BrowserScreen(viewModel = viewModel)
            }
        }
    }

    override fun onEnterFullScreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        Log.d("ShadhinMedia", "MainActivity.onEnterFullScreen: attaching to decorView FrameLayout")
        customVideoView = view
        customVideoViewCallback = callback
        originalOrientation = requestedOrientation

        fullscreenContainer.removeAllViews()
        fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        fullscreenContainer.visibility = View.VISIBLE

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Immersive sticky mode
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onExitFullScreen() {
        Log.d("ShadhinMedia", "MainActivity.onExitFullScreen: restoring system UI and orientation")
        if (customVideoView != null) {
            fullscreenContainer.removeAllViews()
            fullscreenContainer.visibility = View.GONE

            try {
                customVideoViewCallback?.onCustomViewHidden()
            } catch (_: Exception) {}
            customVideoView = null
            customVideoViewCallback = null

            requestedOrientation = originalOrientation

            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("ShadhinMedia", "MainActivity.onPause: pausing all live WebViews and timers")
        viewModel.tabManager.pauseAll()
    }

    override fun onResume() {
        super.onResume()
        Log.d("ShadhinMedia", "MainActivity.onResume: resuming all live WebViews and timers")
        viewModel.tabManager.resumeAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.fullScreenHost = null
    }
}
