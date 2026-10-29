package com.example

import android.Manifest
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.PermissionRequestScreen
import com.example.ui.components.VideoFeedScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.VideoFeedViewModel
import com.example.ui.viewmodel.VideoFeedViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: VideoFeedViewModel by viewModels {
        VideoFeedViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current

                val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_VIDEO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                var hasPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            requiredPermission
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasPermission = isGranted
                    viewModel.onPermissionGranted()
                }

                LaunchedEffect(Unit) {
                    viewModel.loadVideos()
                }

                LaunchedEffect(hasPermission) {
                    if (hasPermission) {
                        viewModel.onPermissionGranted()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
                ) { _ ->
                    com.example.ui.navigation.AppNavigation(
                        uiState = uiState,
                        viewModel = viewModel,
                        onEnterPiP = { enterSystemPiP() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    fun enterSystemPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(9, 16))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                try {
                    val params = PictureInPictureParams.Builder().build()
                    enterPictureInPictureMode(params)
                } catch (e2: Exception) {
                    // Ignore if device does not support PiP
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.updatePictureInPictureMode(isInPictureInPictureMode)
        if (!isInPictureInPictureMode && !isChangingConfigurations) {
            // Exited PiP back to full screen or background
        }
    }

    override fun onPause() {
        super.onPause()
        val inPiP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isInPictureInPictureMode
        } else false
        if (!inPiP) {
            viewModel.pausePlayback()
        }
    }

    override fun onStop() {
        super.onStop()
        val inPiP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isInPictureInPictureMode
        } else false
        if (!inPiP) {
            viewModel.pausePlayback()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.pausePlayback()
    }
}
