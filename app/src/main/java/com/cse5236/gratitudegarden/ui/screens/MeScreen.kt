package com.cse5236.gratitudegarden.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cse5236.gratitudegarden.ui.components.PgTextField
import com.cse5236.gratitudegarden.ui.components.PillButton
import com.cse5236.gratitudegarden.ui.me.MeViewModel
import com.cse5236.gratitudegarden.util.findActivity
import com.cse5236.gratitudegarden.ui.sprites.CoinIcon
import com.cse5236.gratitudegarden.ui.sprites.MaturePlant
import com.cse5236.gratitudegarden.ui.sprites.PgIcon
import com.cse5236.gratitudegarden.ui.sprites.PgIconName
import com.cse5236.gratitudegarden.ui.sprites.PlantPalette
import com.cse5236.gratitudegarden.ui.theme.Caprasimo
import com.cse5236.gratitudegarden.ui.theme.Nunito
import com.cse5236.gratitudegarden.ui.theme.PgAccent
import com.cse5236.gratitudegarden.ui.theme.PgBgCream
import com.cse5236.gratitudegarden.ui.theme.PgBgSage
import com.cse5236.gratitudegarden.ui.theme.PgInk
import com.cse5236.gratitudegarden.ui.theme.PgInkMuted
import com.cse5236.gratitudegarden.ui.theme.PgInkSoft
import com.cse5236.gratitudegarden.ui.theme.PgMoss
import com.cse5236.gratitudegarden.ui.theme.PgPrimary
import com.cse5236.gratitudegarden.ui.theme.PgPrimaryDeep

@Composable
fun MeScreen(onSignOut: () -> Unit, onOpenAdmin: () -> Unit = {}) {
    val vm: MeViewModel = viewModel(factory = MeViewModel.Factory)
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 0 = hidden, 1 = "are you sure" warning, 2 = password confirmation.
    var deleteStep by remember { mutableStateOf(0) }
    var password by remember { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }

    // Re-check the OS notification toggle whenever we come back to the foreground
    // (e.g. the user just toggled it in system settings).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android 13+: enabling the switch may require the runtime grant first.
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // Turn on regardless — the worker no-ops without the grant and the card
        // below shows the "notifications are off" hint if it was denied.
        vm.setReminderEnabled(true)
        vm.refreshPermissions()
    }

    // Permissions-menu launchers: just re-read state after a request (they don't
    // touch the reminder toggle — that's the reminder card's job).
    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> vm.refreshPermissions() }
    val notifCardPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> vm.refreshPermissions() }
    var micAsked by rememberSaveable { mutableStateOf(false) }
    var notifAsked by rememberSaveable { mutableStateOf(false) }

    fun onToggleReminder(enabled: Boolean) {
        if (!enabled) {
            vm.setReminderEnabled(false)
            return
        }
        val needsGrant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsGrant) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else vm.setReminderEnabled(true)
    }

    fun openOsNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        context.startActivity(intent)
    }

    // App info → Permissions, where the user can revoke a granted permission (an
    // app can't revoke its own permissions programmatically).
    fun openAppDetailsSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        context.startActivity(intent)
    }

    // Turn a permission on: prompt if we still can, otherwise it's blocked
    // ("don't ask again") so send the user to Settings.
    fun enableMic() {
        val activity = context.findActivity()
        val canPrompt = !micAsked ||
            (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO))
        if (canPrompt) {
            micAsked = true
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            openAppDetailsSettings()
        }
    }

    fun enableNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val activity = context.findActivity()
            val canPrompt = !notifAsked ||
                (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS))
            if (canPrompt) {
                notifAsked = true
                notifCardPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openOsNotificationSettings()
            }
        } else {
            openOsNotificationSettings()  // no runtime notification permission below API 33
        }
    }

    fun cancelDelete() {
        deleteStep = 0
        password = ""
        vm.clearDeleteError()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PgBgSage)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(text = "Me", fontFamily = Caprasimo, fontSize = 28.sp, color = PgPrimaryDeep)
        Spacer(Modifier.height(16.dp))

        // Notification settings — kept at the very top of the page.
        NotificationSettingsCard(
            enabled = ui.reminderEnabled,
            hour = ui.reminderHour,
            minute = ui.reminderMinute,
            osEnabled = ui.osNotificationsEnabled,
            onToggle = { onToggleReminder(it) },
            onPickTime = { showTimePicker = true },
            onOpenOsSettings = { openOsNotificationSettings() },
        )
        Spacer(Modifier.height(16.dp))

        // Permission management — enable/disable mic + notifications at any time.
        PermissionsCard(
            micGranted = ui.micGranted,
            notifEnabled = ui.osNotificationsEnabled,
            onEnableMic = { enableMic() },
            onManageMic = { openAppDetailsSettings() },
            onEnableNotifications = { enableNotifications() },
            onManageNotifications = { openOsNotificationSettings() },
        )
        Spacer(Modifier.height(20.dp))

        // Profile row
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(PgMoss),
                contentAlignment = Alignment.Center,
            ) {
                MaturePlant(colors = PlantPalette.Rose, size = 48.dp)
            }
            Column {
                Text(
                    text = ui.name.ifBlank { "Gardener" },
                    fontFamily = Caprasimo,
                    fontSize = 22.sp,
                    color = PgInk,
                )
                Text(
                    text = "Level ${ui.level} gardener",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = PgInkSoft,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Stat cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(modifier = Modifier.weight(1f), value = "${ui.streak}", label = "Day streak") {
                PgIcon(name = PgIconName.Flame, color = PgAccent, size = 22.dp)
            }
            StatCard(modifier = Modifier.weight(1f), value = "${ui.totalEntries}", label = "Thoughts") {
                PgIcon(name = PgIconName.Mic, color = PgPrimaryDeep, size = 20.dp)
            }
            StatCard(modifier = Modifier.weight(1f), value = "${ui.coins}", label = "Coins") {
                CoinIcon(size = 22.dp)
            }
        }

        Spacer(Modifier.height(28.dp))

        // Admin-only entry point. Hidden entirely for normal users.
        if (ui.isAdmin) {
            PillButton(
                text = "Admin Dashboard",
                onClick = onOpenAdmin,
                primary = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }

        PillButton(
            text = "Log out",
            onClick = onSignOut,
            primary = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        // Destructive, deliberately understated — self-service account deletion.
        Text(
            text = "Delete account",
            fontFamily = Nunito,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = PgAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(percent = 50))
                .clickable { deleteStep = 1 }
                .padding(vertical = 10.dp),
        )
        Spacer(Modifier.height(12.dp))
    }

    // Step 1 — confirm intent.
    if (deleteStep == 1) {
        AlertDialog(
            onDismissRequest = { cancelDelete() },
            title = { Text("Delete your account?", fontFamily = Caprasimo, color = PgInk) },
            text = {
                Text(
                    "This permanently deletes your account and erases everything — your garden, " +
                        "journal entries, coins, and stats. This can't be undone.",
                    fontFamily = Nunito,
                    color = PgInkSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.clearDeleteError(); deleteStep = 2 }) {
                    Text("Continue", color = PgAccent, fontFamily = Nunito, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelDelete() }) {
                    Text("Cancel", color = PgInkSoft, fontFamily = Nunito)
                }
            },
        )
    }

    // Step 2 — re-authenticate with the password, then delete for real.
    if (deleteStep == 2) {
        AlertDialog(
            onDismissRequest = { if (!ui.deleting) cancelDelete() },
            title = { Text("Confirm deletion", fontFamily = Caprasimo, color = PgInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter your password to permanently delete this account. There's no going back.",
                        fontFamily = Nunito,
                        color = PgInkSoft,
                    )
                    PgTextField(
                        value = password,
                        onValueChange = { password = it; vm.clearDeleteError() },
                        placeholder = "Password",
                        icon = PgIconName.Lock,
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    )
                    ui.deleteError?.let {
                        Text(
                            text = it,
                            fontFamily = Nunito,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = PgAccent,
                        )
                    }
                }
            },
            confirmButton = {
                val canDelete = !ui.deleting && password.isNotBlank()
                TextButton(onClick = { vm.deleteAccount(password) }, enabled = canDelete) {
                    Text(
                        text = if (ui.deleting) "Deleting…" else "Delete forever",
                        color = if (canDelete) PgAccent else PgInkMuted,
                        fontFamily = Nunito,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelDelete() }, enabled = !ui.deleting) {
                    Text("Cancel", color = PgInkSoft, fontFamily = Nunito)
                }
            },
        )
    }

    if (showTimePicker) {
        ReminderTimePickerDialog(
            initialHour = ui.reminderHour,
            initialMinute = ui.reminderMinute,
            onConfirm = { h, m -> vm.setReminderTime(h, m); showTimePicker = false },
            onDismiss = { showTimePicker = false },
        )
    }
}

// ── Notification settings card (top of the Me page) ──────────────────────
@Composable
private fun NotificationSettingsCard(
    enabled: Boolean,
    hour: Int,
    minute: Int,
    osEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit,
    onOpenOsSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(PgMoss),
                contentAlignment = Alignment.Center,
            ) {
                PgIcon(name = PgIconName.Flame, color = PgAccent, size = 20.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Daily reminder",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PgInk,
                )
                Text(
                    "A nudge to keep your gratitude streak",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                    color = PgInkSoft,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PgPrimary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = PgInkMuted,
                ),
            )
        }

        // Time-of-day row — tappable when reminders are enabled.
        val timeColor = if (enabled) PgPrimaryDeep else PgInkMuted
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .then(if (enabled) Modifier.clickable(onClick = onPickTime) else Modifier)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Reminder time",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (enabled) PgInk else PgInkMuted,
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatTime(hour, minute),
                fontFamily = Nunito,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = timeColor,
            )
        }

        if (enabled && !osEnabled) {
            Text(
                "Notifications are off for Gratitude Garden in your system settings, " +
                    "so reminders won't appear. Tap to open settings.",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = PgAccent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenOsSettings)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

// ── Permissions card ─────────────────────────────────────────────────────
@Composable
private fun PermissionsCard(
    micGranted: Boolean,
    notifEnabled: Boolean,
    onEnableMic: () -> Unit,
    onManageMic: () -> Unit,
    onEnableNotifications: () -> Unit,
    onManageNotifications: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(16.dp),
    ) {
        Text("Permissions", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PgInk)
        Text(
            "Turn these on or off any time. Turning one off opens system settings.",
            fontFamily = Nunito,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = PgInkMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        PermissionRow(
            icon = PgIconName.Mic,
            title = "Microphone",
            subtitle = "Speak your gratitude entries",
            granted = micGranted,
            onEnable = onEnableMic,
            onManage = onManageMic,
        )
        Spacer(Modifier.height(10.dp))
        PermissionRow(
            icon = PgIconName.Flame,
            title = "Notifications",
            subtitle = "Daily gratitude reminders",
            granted = notifEnabled,
            onEnable = onEnableNotifications,
            onManage = onManageNotifications,
        )
    }
}

@Composable
private fun PermissionRow(
    icon: PgIconName,
    title: String,
    subtitle: String,
    granted: Boolean,
    onEnable: () -> Unit,
    onManage: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(PgMoss),
            contentAlignment = Alignment.Center,
        ) {
            PgIcon(name = icon, color = if (granted) PgPrimaryDeep else PgInkMuted, size = 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PgInk)
            Text(subtitle, fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = PgInkSoft)
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            // Allowed → tap to manage / revoke in system settings.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PgMoss)
                    .clickable(onClick = onManage)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(PgPrimary))
                Spacer(Modifier.width(6.dp))
                Text("Allowed", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = PgPrimaryDeep)
            }
        } else {
            Text(
                "Turn on",
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = PgBgCream,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PgPrimary)
                    .clickable(onClick = onEnable)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder time", fontFamily = Caprasimo, color = PgInk) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("Set", color = PgPrimaryDeep, fontFamily = Nunito, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PgInkSoft, fontFamily = Nunito)
            }
        },
    )
}

/** 24h hour/minute → "8:00 PM" (locale-independent). */
private fun formatTime(hour: Int, minute: Int): String {
    val h12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val amPm = if (hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(h12, minute, amPm)
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon()
        Text(text = value, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = PgInk)
        Text(text = label, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = PgInkMuted)
    }
}
