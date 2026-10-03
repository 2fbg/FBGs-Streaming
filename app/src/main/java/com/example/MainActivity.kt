package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.example.ui.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    private var viewModelReference: AppViewModel? = null

    private val pipReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val action = intent?.action ?: return
            val viewModel = viewModelReference ?: return
            when (action) {
                "com.example.ACTION_PLAY_PAUSE" -> {
                    viewModel.togglePlayPause()
                }
                "com.example.ACTION_NEXT" -> {
                    viewModel.playNext()
                }
                "com.example.ACTION_PREVIOUS" -> {
                    viewModel.playPrevious()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize autonomous Crash Logger to capture full diagnostics and stack traces
        com.example.utils.CrashLogger.init(applicationContext)

        // Proper edgeToEdge execution to extend views elegantly under notches
        enableEdgeToEdge()
        
        // Register Broadcast Receiver for Picture-in-Picture controls
        val filter = android.content.IntentFilter().apply {
            addAction("com.example.ACTION_PLAY_PAUSE")
            addAction("com.example.ACTION_NEXT")
            addAction("com.example.ACTION_PREVIOUS")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(pipReceiver, filter)
        }

        setContent {
            val viewModel: AppViewModel = viewModel()
            viewModelReference = viewModel
            val useAmoledMode by viewModel.useAmoledMode.collectAsState()
            val isInPipMode by viewModel.isInPipMode.collectAsState()
            val isPlaying by viewModel.isPlayerPlaying.collectAsState()

            // Observe playback state changes to update Picture-in-Picture controls dynamically
            LaunchedEffect(isInPipMode, isPlaying) {
                if (isInPipMode) {
                    updatePipParams(isPlaying)
                }
            }
            
            MyApplicationTheme(useAmoledMode = useAmoledMode) {
                AppNavigation(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pipReceiver)
        } catch (e: Exception) {
            // Ignore if already unregistered or not registered
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            try {
                coil.Coil.imageLoader(this).memoryCache?.clear()
                System.gc()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun getPipParams(isPlaying: Boolean): android.app.PictureInPictureParams? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val actions = ArrayList<android.app.RemoteAction>()
            
            // Previous action intent
            val prevIntent = android.app.PendingIntent.getBroadcast(
                this, 1, android.content.Intent("com.example.ACTION_PREVIOUS").apply {
                    setPackage(packageName)
                },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val prevIcon = android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_previous)
            val prevAction = android.app.RemoteAction(prevIcon, "Anterior", "Canal Anterior", prevIntent)
            actions.add(prevAction)

            // Play/Pause action intent
            val playPauseIntent = android.app.PendingIntent.getBroadcast(
                this, 2, android.content.Intent("com.example.ACTION_PLAY_PAUSE").apply {
                    setPackage(packageName)
                },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val playPauseIconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val playPauseIcon = android.graphics.drawable.Icon.createWithResource(this, playPauseIconRes)
            val playPauseTitle = if (isPlaying) "Pausar" else "Reproduzir"
            val playPauseAction = android.app.RemoteAction(playPauseIcon, playPauseTitle, playPauseTitle, playPauseIntent)
            actions.add(playPauseAction)

            // Next action intent
            val nextIntent = android.app.PendingIntent.getBroadcast(
                this, 3, android.content.Intent("com.example.ACTION_NEXT").apply {
                    setPackage(packageName)
                },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val nextIcon = android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_next)
            val nextAction = android.app.RemoteAction(nextIcon, "Próximo", "Próximo Canal", nextIntent)
            actions.add(nextAction)

            val aspectRational = android.util.Rational(16, 9)
            return android.app.PictureInPictureParams.Builder()
                .setAspectRatio(aspectRational)
                .setActions(actions)
                .build()
        }
        return null
    }

    private fun updatePipParams(isPlaying: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                getPipParams(isPlaying)?.let { setPictureInPictureParams(it) }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error setting PiP params", e)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModelReference?.setIsInPipMode(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            val isPlaying = viewModelReference?.isPlayerPlaying?.value ?: true
            updatePipParams(isPlaying)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val viewModel = viewModelReference
        if (viewModel != null && viewModel.currentPlayingItem.value != null && !viewModel.isInPipMode.value) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    val isPlaying = viewModel.isPlayerPlaying.value
                    getPipParams(isPlaying)?.let { enterPictureInPictureMode(it) }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to auto-enter PiP mode", e)
                }
            }
        }
    }
}
