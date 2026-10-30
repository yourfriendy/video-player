package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.update.ReleaseInfo
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.viewmodel.AppUpdateViewModel
import com.example.ui.viewmodel.UpdateState

private val UpdateCyan = Color(0xFF5EEAD4)
private val UpdateMuted = Color(0xFF94A3B8)
private val UpdateWarn = Color(0xFFF59E0B)

/** "Build 12 · built 2026-10-30 09:41 UTC · 5.1 MB" */
private fun describe(release: ReleaseInfo): String = buildString {
    append("Build ${release.buildNumber}")
    release.builtAt?.let { append(" · built $it") }
    if (release.apkSize > 0) append(" · ${"%.1f".format(release.apkSize / 1_000_000.0)} MB")
}

private const val PERMISSION_HINT =
    "Android opened a screen asking you to allow OffScroll to install apps. Switch it on, come back here and tap Install."

private const val INSTALL_HINT =
    "Android will ask you to confirm. Your videos and settings are kept."

// ------------------------------------------------------------------------------------------------
// Pop-up, shown over any screen when a new build is found at launch
// ------------------------------------------------------------------------------------------------

@Composable
fun AppUpdateDialog(viewModel: AppUpdateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val visible by viewModel.promptVisible.collectAsStateWithLifecycle()
    if (!visible) return

    when (val s = state) {
        is UpdateState.Available -> UpdateAlert(
            title = "Update available",
            confirmLabel = "Update now",
            onConfirm = { viewModel.startDownload() },
            dismissLabel = "Later",
            onDismiss = { viewModel.dismissPrompt() }
        ) {
            UpdateBody("A newer OffScroll is ready to download.", describe(s.release))
        }

        is UpdateState.Downloading -> UpdateAlert(
            title = "Downloading update…",
            confirmLabel = null,
            onConfirm = {},
            dismissLabel = "Hide",
            onDismiss = { viewModel.dismissPrompt() }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = UpdateCyan,
                    trackColor = Color(0x33FFFFFF)
                )
                Text("${(s.progress * 100).toInt()}%", color = UpdateMuted, fontSize = 13.sp)
            }
        }

        is UpdateState.ReadyToInstall -> UpdateAlert(
            title = "Ready to install",
            confirmLabel = "Install",
            onConfirm = { viewModel.install() },
            dismissLabel = "Later",
            onDismiss = { viewModel.dismissPrompt() }
        ) {
            UpdateBody(if (s.needsPermission) PERMISSION_HINT else INSTALL_HINT, describe(s.release))
        }

        is UpdateState.Failed -> UpdateAlert(
            title = "Update problem",
            confirmLabel = if (s.release != null) "Try again" else null,
            onConfirm = { viewModel.startDownload() },
            dismissLabel = "Close",
            onDismiss = { viewModel.dismissPrompt() }
        ) {
            Text(s.message, color = UpdateMuted, fontSize = 14.sp, lineHeight = 20.sp)
        }

        else -> Unit
    }
}

@Composable
private fun UpdateAlert(
    title: String,
    confirmLabel: String?,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = content,
        confirmButton = {
            if (confirmLabel != null) {
                TextButton(onClick = onConfirm) {
                    Text(confirmLabel, color = UpdateCyan, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel, color = UpdateMuted) }
        }
    )
}

@Composable
private fun UpdateBody(message: String, details: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
        Text(details, color = UpdateMuted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

// ------------------------------------------------------------------------------------------------
// "App updates" card on the Settings screen
// ------------------------------------------------------------------------------------------------

private class UpdateButtonSpec(val label: String, val enabled: Boolean, val onClick: () -> Unit)

@Composable
fun AppUpdateCard(viewModel: AppUpdateViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state
    val shape = RoundedCornerShape(18.dp)

    val button = when (s) {
        is UpdateState.Checking -> UpdateButtonSpec("Checking…", false) {}
        is UpdateState.Downloading -> UpdateButtonSpec("Downloading…", false) {}
        is UpdateState.Available -> UpdateButtonSpec("Update now", true) { viewModel.startDownload() }
        is UpdateState.ReadyToInstall -> UpdateButtonSpec("Install", true) { viewModel.install() }
        is UpdateState.Failed ->
            if (s.release != null) UpdateButtonSpec("Try again", true) { viewModel.startDownload() }
            else UpdateButtonSpec("Check again", true) { viewModel.checkNow() }
        else -> UpdateButtonSpec("Check for updates", true) { viewModel.checkNow() }
    }

    val status: String = when (s) {
        is UpdateState.Idle -> "New builds are published on this app's GitHub releases page."
        is UpdateState.Checking -> "Checking GitHub…"
        is UpdateState.UpToDate -> "You're on the latest build."
        is UpdateState.Available -> "Update available: ${describe(s.release)}"
        is UpdateState.Downloading -> "Downloading… ${(s.progress * 100).toInt()}%"
        is UpdateState.ReadyToInstall -> if (s.needsPermission) PERMISSION_HINT else "Downloaded. $INSTALL_HINT"
        is UpdateState.Failed -> s.message
    }
    val statusColor = if (s is UpdateState.Failed) UpdateWarn else UpdateMuted

    Card(
        modifier = modifier.fillMaxWidth().testTag("app_update_card"),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GlassBorder, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "OffScroll ${viewModel.versionName}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Installed build ${viewModel.installedBuild}",
                        color = UpdateMuted,
                        fontSize = 13.sp
                    )
                }
            }

            Text(text = status, color = statusColor, fontSize = 13.sp, lineHeight = 18.sp)

            if (s is UpdateState.Downloading) {
                LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = UpdateCyan,
                    trackColor = Color(0x33FFFFFF)
                )
            }

            Button(
                onClick = button.onClick,
                enabled = button.enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("app_update_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UpdateCyan,
                    contentColor = Color(0xFF020617),
                    disabledContainerColor = Color(0x33FFFFFF),
                    disabledContentColor = UpdateMuted
                )
            ) {
                Text(button.label, fontWeight = FontWeight.Bold)
            }
        }
    }
}
