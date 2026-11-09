package com.example.ui.components

import com.example.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.download.InstagramNotificationListener
import com.example.data.download.PeopleStore
import com.example.data.download.SavedPerson
import com.example.data.download.StoryAlertScheduler
import com.example.data.download.StoryAlertSettings
import com.example.data.download.StoryAlertStatus
import com.example.data.download.StoryAlertWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Everything about notifications, in one calm screen that matches the rest of the app (not the Instagram look of
 * the profile viewer): is it working, who to watch, how often, instant alerts, background reliability, testing.
 */
@Composable
internal fun AlertsScreen(
    people: List<SavedPerson>,
    onSetAlerts: (String, Boolean) -> Unit,
    onOpenPerson: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Phone-side facts (permission, battery, instant access, last check) change outside the app: re-read them often
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2_000L)
            tick++
        }
    }
    val notificationsOk = remember(tick) { !StoryAlertWorker.notificationsBlocked(context) }
    val batteryOk = remember(tick) { isIgnoringBattery(context) }
    val instantOn = remember(tick) { InstagramNotificationListener.isEnabled(context) }
    val lastCheck = remember(tick) { StoryAlertStatus.read(context) }
    var minutes by remember { mutableIntStateOf(StoryAlertSettings.intervalMinutes(context)) }
    val watching = people.count { it.alerts }
    val live = minutes <= StoryAlertSettings.LIVE_MAX_MINUTES

    // ---- permission handling ----
    var pendingPerson by remember { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPerson?.let { onSetAlerts(it, true) }
        pendingPerson = null
        if (!granted) {
            Feedback.show("Notifications are off for OffScroll, so alerts can't be shown.")
            openNotificationSettings(context)
        }
    }
    fun needsPermissionPrompt() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun fixNotifications() {
        if (needsPermissionPrompt()) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else openNotificationSettings(context)
    }

    fun setPerson(name: String, on: Boolean) {
        if (on && needsPermissionPrompt()) {
            pendingPerson = name
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onSetAlerts(name, on)
            if (on && StoryAlertWorker.notificationsBlocked(context)) fixNotifications()
        }
    }

    Box(modifier = modifier.fillMaxSize().testTag("alerts_screen")) {
        AuroraBackground()
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("alerts_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column {
                    Text(stringResource(R.string.alerts), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(R.string.be_told_the_moment_someone_posts), color = DlMuted, fontSize = 12.sp)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ---------------- Status ----------------
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradientTile(
                            icon = if (watching > 0) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                            size = 56.dp,
                            colors = if (watching > 0) DlSunset else listOf(Color(0xFF475569), Color(0xFF334155))
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (watching) {
                                    0 -> "Alerts are off"
                                    1 -> "Watching 1 person"
                                    else -> "Watching $watching people"
                                },
                                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (watching == 0) "Switch someone on below to start."
                                else "New stories and reposts, checked every $minutes min" + if (live) " (live)" else "",
                                color = DlSoftText.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(
                            label = if (notificationsOk) "Notifications" else "Notifications off",
                            ok = notificationsOk, icon = if (notificationsOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                            onClick = { fixNotifications() }
                        )
                        StatusChip(
                            label = if (batteryOk) "Background" else "Background limited",
                            ok = batteryOk, icon = if (batteryOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                            onClick = { requestBatteryExemption(context) }
                        )
                        StatusChip(
                            label = if (instantOn) "Instant" else "Instant off",
                            ok = instantOn, icon = if (instantOn) Icons.Default.Bolt else Icons.Default.Warning,
                            onClick = { openListenerSettings(context) }
                        )
                    }
                    Text(
                        text = if (!notificationsOk || !batteryOk) "Tap an amber chip to fix it." else "Everything needed is allowed.",
                        color = DlMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // ---------------- Who ----------------
                GlassCard {
                    SectionTitle("Who to watch", "Stories and reposts. Nobody is told you looked.")
                    Spacer(modifier = Modifier.height(10.dp))
                    if (people.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x12FFFFFF))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Group, contentDescription = null, tint = DlMuted, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.no_one_saved_yet_open_watch),
                                color = DlSoftText, fontSize = 13.sp, lineHeight = 18.sp
                            )
                        }
                    } else {
                        people.forEachIndexed { i, person ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onOpenPerson(person.username) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Avatar(person.picUrl, 46.dp, initial = person.username)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            if (person.fullName.isNotBlank()) person.fullName else "@${person.username}",
                                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                                        )
                                        Text(
                                            if (person.alerts) "@${person.username} · alerts on" else "@${person.username} · alerts off",
                                            color = if (person.alerts) DlCyan else DlMuted, fontSize = 12.sp, maxLines = 1
                                        )
                                    }
                                }
                                Switch(
                                    checked = person.alerts,
                                    onCheckedChange = { setPerson(person.username, it) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = DlPink, checkedThumbColor = Color.White),
                                    modifier = Modifier.testTag("alert_switch_${person.username}")
                                )
                            }
                            if (i < people.lastIndex) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
                            }
                        }
                    }
                }

                // ---------------- How often ----------------
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradientTile(Icons.Default.Timer, 40.dp, listOf(DlViolet, DlCyan))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            SectionTitle("How often", "Pick your own timer")
                        }
                        Text(
                            text = "$minutes min",
                            color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = { minutes = it.roundToInt() },
                        onValueChangeFinished = { applyMinutes(context, minutes) },
                        valueRange = StoryAlertSettings.MIN_MINUTES.toFloat()..StoryAlertSettings.MAX_MINUTES.toFloat(),
                        steps = StoryAlertSettings.MAX_MINUTES - StoryAlertSettings.MIN_MINUTES - 1,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = DlPink, inactiveTrackColor = Color(0x33FFFFFF)),
                        modifier = Modifier.padding(top = 4.dp).testTag("alert_timer_slider")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 5, 10, 30, 60).forEach { preset ->
                            val active = minutes == preset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (active) Brush.horizontalGradient(DlSunset) else Brush.horizontalGradient(listOf(Color(0x14FFFFFF), Color(0x14FFFFFF))))
                                    .clickable {
                                        minutes = preset
                                        applyMinutes(context, preset)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$preset", color = if (active) Color.White else DlSoftText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    StatusChip(
                        label = if (live) "Live mode" else "Normal mode",
                        ok = true, icon = if (live) Icons.Default.Bolt else Icons.Default.CheckCircle
                    )
                    Text(
                        text = if (live) {
                            "A small ongoing notification stays on so Android doesn't pause the checks. A new story or repost alerts you right after the check that finds it. Very short timers use more battery and send Instagram more requests; if Instagram asks you to verify, pick a longer one."
                        } else {
                            "Android may run a check a little late while the phone is idle. Pick 10 min or less for live mode."
                        },
                        color = DlMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // ---------------- Instant ----------------
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradientTile(Icons.Default.Bolt, 40.dp, listOf(DlOrange, DlPink))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) { SectionTitle("Instant alerts", "Faster than any timer, for stories") }
                        StatusChip(label = if (instantOn) "On" else "Off", ok = instantOn, icon = if (instantOn) Icons.Default.CheckCircle else Icons.Default.Warning)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf(
                        "Open the Instagram app and go to their profile.",
                        "Tap the bell and switch on Stories.",
                        "Allow OffScroll to read Instagram's notifications. Only Instagram's are looked at."
                    ).forEachIndexed { i, text ->
                        Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(0x1FFFFFFF)),
                                contentAlignment = Alignment.Center
                            ) { Text("${i + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text, color = DlSoftText, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    if (!instantOn) {
                        Spacer(modifier = Modifier.height(10.dp))
                        GradientButton(text = "Allow instant alerts", icon = Icons.Default.Bolt, onClick = { openListenerSettings(context) })
                        Text(
                            "If Android says \"Restricted setting\": Settings > Apps > OffScroll > ⋮ > Allow restricted settings, then try again.",
                            color = DlMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Text(
                        stringResource(R.string.reposts_have_no_instagram_notification_so),
                        color = DlMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // ---------------- Background ----------------
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradientTile(Icons.Default.BatteryChargingFull, 40.dp, listOf(DlGood, DlCyan))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) { SectionTitle("Keep it running", "Stop Android pausing OffScroll") }
                        StatusChip(label = if (batteryOk) "Allowed" else "Limited", ok = batteryOk, icon = if (batteryOk) Icons.Default.CheckCircle else Icons.Default.Warning)
                    }
                    Text(
                        stringResource(R.string.phones_pause_apps_that_sit_idle),
                        color = DlSoftText, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp)
                    )
                    if (!batteryOk) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlineButton(text = "Allow background activity", onClick = { requestBatteryExemption(context) }, modifier = Modifier.fillMaxWidth())
                    }
                }

                // ---------------- Test ----------------
                GlassCard {
                    SectionTitle("Make sure it works")
                    Text(
                        text = lastCheck?.let { (at, line) ->
                            "Last check " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(at)) + ": " + line
                        } ?: "No check has run yet. The first one only notes what is already posted.",
                        color = DlSoftText, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlineButton(
                            text = "Send test alert",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val ok = StoryAlertWorker.sendTest(context)
                                Feedback.show(if (ok) "Test alert sent" else "Notifications are off for OffScroll")
                                if (!ok) fixNotifications()
                            }
                        )
                        OutlineButton(
                            text = "Check now",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                StoryAlertScheduler.checkNow(context)
                                Feedback.show("Checking now. The result shows here in a minute.")
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun applyMinutes(context: Context, minutes: Int) {
    StoryAlertSettings.setIntervalMinutes(context, minutes)
    StoryAlertScheduler.sync(context, restart = true)
}

private fun isIgnoringBattery(context: Context): Boolean = try {
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
} catch (e: Exception) {
    true
}

private fun openNotificationSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        // No settings screen to open
    }
}

private fun openListenerSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Feedback.show("Open Settings > Notifications > Notification access and allow OffScroll")
    }
}

private fun requestBatteryExemption(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: Exception) {
            // Nothing to open on this phone
        }
    }
}
