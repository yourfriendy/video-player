package com.example

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.PermissionRequestScreen
import com.example.ui.components.VideoFeedScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppUpdateViewModel
import com.example.ui.viewmodel.VideoFeedViewModel
import com.example.ui.viewmodel.VideoFeedViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: VideoFeedViewModel by viewModels {
        VideoFeedViewModelFactory(applicationContext)
    }

    // Shared by the update pop-up below and the "App updates" card in Settings
    private val updateViewModel: AppUpdateViewModel by viewModels()

    private val requiredPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun hasMediaPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, requiredPermission) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Tell the ViewModel the real permission state before the first frame (it loads videos if granted)
        viewModel.onPermissionResult(hasMediaPermission())

        // Launched from Instagram's Share sheet? (Only on a fresh start; rotation must not re-trigger it.)
        if (savedInstanceState == null) handleShareIntent(intent)

        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                var hasAskedForPermission by rememberSaveable { mutableStateOf(false) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    viewModel.onPermissionResult(isGranted)
                }

                val needsPermissionScreen = !uiState.isPermissionGranted && !uiState.isDemoMode

                // Actually ask for the permission (this was never requested before)
                LaunchedEffect(needsPermissionScreen) {
                    if (needsPermissionScreen && !hasAskedForPermission) {
                        hasAskedForPermission = true
                        permissionLauncher.launch(requiredPermission)
                    }
                }

                if (needsPermissionScreen) {
                    PermissionRequestScreen(
                        onRequestPermission = {
                            // After "don't ask again" the system dialog no longer appears: go to Settings
                            if (hasAskedForPermission &&
                                !ActivityCompat.shouldShowRequestPermissionRationale(
                                    this@MainActivity, requiredPermission
                                )
                            ) {
                                openAppSettings()
                            } else {
                                permissionLauncher.launch(requiredPermission)
                            }
                        },
                        onLoadDemoClips = { viewModel.loadDemoClips() }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
                    ) { _ ->
                        com.example.ui.navigation.AppNavigation(
                            uiState = uiState,
                            viewModel = viewModel,
                            updateViewModel = updateViewModel,
                            onEnterPiP = { enterSystemPiP() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    AppUpdateDialog(updateViewModel)
                }
            }
        }
    }

    /** The app is already running and another app shared a link to it. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
        // Consume it so the same share is never handled twice
        intent.action = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        viewModel.handleSharedText(shared)
    }

    override fun onResume() {
        super.onResume()
        // Picks up permission granted/revoked in system Settings, and resumes playback that onPause stopped
        viewModel.onPermissionResult(hasMediaPermission())
        viewModel.resumePlayback()
        // Looks for a newer build on GitHub (skips the network if it checked in the last 15 minutes)
        updateViewModel.checkOnLaunch()
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
