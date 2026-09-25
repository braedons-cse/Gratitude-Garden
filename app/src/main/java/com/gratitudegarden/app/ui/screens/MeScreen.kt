package com.gratitudegarden.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gratitudegarden.app.data.LevelProgress
import com.gratitudegarden.app.data.MAX_STREAK_FREEZES
import com.gratitudegarden.app.ui.admin.AdminTools
import com.gratitudegarden.app.ui.components.GgTextField
import com.gratitudegarden.app.ui.components.PillButton
import com.gratitudegarden.app.ui.me.MeViewModel
import com.gratitudegarden.app.util.findActivity
import com.gratitudegarden.app.util.formatTime
import com.gratitudegarden.app.ui.sprites.CoinIcon
import com.gratitudegarden.app.ui.sprites.MaturePlant
import com.gratitudegarden.app.ui.sprites.GgIcon
import com.gratitudegarden.app.ui.sprites.GgIconName
import com.gratitudegarden.app.ui.sprites.PlantPalette
import com.gratitudegarden.app.ui.theme.Caprasimo
import com.gratitudegarden.app.ui.theme.Nunito
import com.gratitudegarden.app.ui.theme.GgAccent
import com.gratitudegarden.app.ui.theme.GgBgCream
import com.gratitudegarden.app.ui.theme.GgBgSage
import com.gratitudegarden.app.ui.theme.GgFrost
import com.gratitudegarden.app.ui.theme.GgFrostLight
import com.gratitudegarden.app.ui.theme.GgInk
import com.gratitudegarden.app.ui.theme.GgInkMuted
import com.gratitudegarden.app.ui.theme.GgInkSoft
import com.gratitudegarden.app.ui.theme.GgMoss
import com.gratitudegarden.app.ui.theme.GgPrimary
import com.gratitudegarden.app.ui.theme.GgPrimaryDeep

@Composable
fun MeScreen(onSignOut: () -> Unit, onOpenAdmin: () -> Unit = {}) {
    val vm: MeViewModel = viewModel(factory = MeViewModel.Factory)
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 0 = hidden, 1 = "are you sure" warning, 2 = password confirmation.
    var deleteStep by remember { mutableStateOf(0) }
    var password by remember { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmLogOut by remember { mutableStateOf(false) }

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

    // Microphone-card launcher: just re-read state after a request (it doesn't
    // touch the reminder toggle — that's the reminder card's job).
    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> vm.refreshPermissions() }
    var micAsked by rememberSaveable { mutableStateOf(false) }

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

    fun cancelDelete() {
        deleteStep = 0
        password = ""
        vm.clearDeleteError()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GgBgSage)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(text = "Me", fontFamily = Caprasimo, fontSize = 28.sp, color = GgPrimaryDeep)
        Spacer(Modifier.height(16.dp))

        // Profile row — name/garden info sits at the very top of the page.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(GgMoss),
                contentAlignment = Alignment.Center,
            ) {
                MaturePlant(colors = PlantPalette.Rose, size = 48.dp)
            }
            Column {
                Text(
                    text = ui.name.ifBlank { "Gardener" },
                    fontFamily = Caprasimo,
                    fontSize = 22.sp,
                    color = GgInk,
                )
                Text(
                    text = "Level ${ui.progress.level} gardener",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = GgInkSoft,
                )
                Spacer(Modifier.height(6.dp))
                LevelBar(ui.progress)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Stat cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(modifier = Modifier.weight(1f), value = "${ui.streak}", label = "Day streak") {
                if (ui.streakHeld) GgIcon(name = GgIconName.Snowflake, color = GgFrost, size = 22.dp)
                else GgIcon(name = GgIconName.Flame, color = GgAccent, size = 22.dp)
            }
            StatCard(modifier = Modifier.weight(1f), value = "${ui.totalEntries}", label = "Thoughts") {
                GgIcon(name = GgIconName.Mic, color = GgPrimaryDeep, size = 20.dp)
            }
            StatCard(modifier = Modifier.weight(1f), value = "${ui.coins}", label = "Coins") {
                CoinIcon(size = 22.dp)
            }
        }

        Spacer(Modifier.height(12.dp))
        FreezeCard(freezes = ui.freezes)

        Spacer(Modifier.height(28.dp))

        // Notification settings — reminder toggle + time, below the profile.
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

        // Microphone gets its own card, mirroring the reminder card's look.
        MicrophoneCard(
            micGranted = ui.micGranted,
            onEnableMic = { enableMic() },
            onManageMic = { openAppDetailsSettings() },
        )
        Spacer(Modifier.height(28.dp))

        // Admin-only entry point. Hidden entirely for normal users, and absent from the
        // consumer build altogether (the dashboard only exists in the staging flavor).
        if (ui.isAdmin && AdminTools.AVAILABLE) {
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
            // Logging out wipes this device's copy, queue included, so unsynced entries
            // would be gone for good: ask first.
            onClick = { if (ui.unsynced > 0) { vm.clearSyncFailed(); confirmLogOut = true } else onSignOut() },
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
            color = GgAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(percent = 50))
                .clickable { deleteStep = 1 }
                .padding(vertical = 10.dp),
        )
        Spacer(Modifier.height(12.dp))
    }

    if (confirmLogOut) {
        UnsyncedLogOutDialog(
            unsynced = ui.unsynced,
            syncing = ui.syncing,
            syncFailed = ui.syncFailed,
            onSyncNow = vm::syncNow,
            onLogOut = { confirmLogOut = false; onSignOut() },
            onDismiss = { confirmLogOut = false },
        )
    }

    // Step 1 — confirm intent.
    if (deleteStep == 1) {
        AlertDialog(
            onDismissRequest = { cancelDelete() },
            title = { Text("Delete your account?", fontFamily = Caprasimo, color = GgInk) },
            text = {
                Text(
                    "This permanently deletes your account and erases everything — your garden, " +
                        "journal entries, coins, and stats. This can't be undone.",
                    fontFamily = Nunito,
                    color = GgInkSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.clearDeleteError(); deleteStep = 2 }) {
                    Text("Continue", color = GgAccent, fontFamily = Nunito, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelDelete() }) {
                    Text("Cancel", color = GgInkSoft, fontFamily = Nunito)
                }
            },
        )
    }

    // Step 2 — re-authenticate with the password, then delete for real.
    if (deleteStep == 2) {
        AlertDialog(
            onDismissRequest = { if (!ui.deleting) cancelDelete() },
            title = { Text("Confirm deletion", fontFamily = Caprasimo, color = GgInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter your password to permanently delete this account. There's no going back.",
                        fontFamily = Nunito,
                        color = GgInkSoft,
                    )
                    GgTextField(
                        value = password,
                        onValueChange = { password = it; vm.clearDeleteError() },
                        placeholder = "Password",
                        icon = GgIconName.Lock,
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
                            color = GgAccent,
                        )
                    }
                }
            },
            confirmButton = {
                val canDelete = !ui.deleting && password.isNotBlank()
                TextButton(onClick = { vm.deleteAccount(password) }, enabled = canDelete) {
                    Text(
                        text = if (ui.deleting) "Deleting…" else "Delete forever",
                        color = if (canDelete) GgAccent else GgInkMuted,
                        fontFamily = Nunito,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelDelete() }, enabled = !ui.deleting) {
                    Text("Cancel", color = GgInkSoft, fontFamily = Nunito)
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
                modifier = Modifier.size(38.dp).clip(CircleShape).background(GgMoss),
                contentAlignment = Alignment.Center,
            ) {
                GgIcon(name = GgIconName.Flame, color = GgAccent, size = 20.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Daily reminder",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = GgInk,
                )
                Text(
                    "A nudge to keep your gratitude streak",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                    color = GgInkSoft,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = GgPrimary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = GgInkMuted,
                ),
            )
        }

        // Time-of-day row — tappable when reminders are enabled.
        val timeColor = if (enabled) GgPrimaryDeep else GgInkMuted
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
                color = if (enabled) GgInk else GgInkMuted,
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
                color = GgAccent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenOsSettings)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

// ── Microphone card (mirrors the reminder card) ──────────────────────────
@Composable
private fun MicrophoneCard(
    micGranted: Boolean,
    onEnableMic: () -> Unit,
    onManageMic: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(16.dp),
    ) {
        PermissionRow(
            icon = GgIconName.Mic,
            title = "Microphone",
            subtitle = "Speak your gratitude entries",
            granted = micGranted,
            onEnable = onEnableMic,
            onManage = onManageMic,
        )
    }
}

@Composable
private fun PermissionRow(
    icon: GgIconName,
    title: String,
    subtitle: String,
    granted: Boolean,
    onEnable: () -> Unit,
    onManage: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(GgMoss),
            contentAlignment = Alignment.Center,
        ) {
            GgIcon(name = icon, color = if (granted) GgPrimaryDeep else GgInkMuted, size = 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GgInk)
            Text(subtitle, fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = GgInkSoft)
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            // Allowed → tap to manage / revoke in system settings.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(GgMoss)
                    .clickable(onClick = onManage)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(GgPrimary))
                Spacer(Modifier.width(6.dp))
                Text("Allowed", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = GgPrimaryDeep)
            }
        } else {
            Text(
                "Turn on",
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = GgBgCream,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(GgPrimary)
                    .clickable(onClick = onEnable)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun ReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // 24h clock → wheel indices: 12-hour display + AM/PM period.
    val initialH12 = if (initialHour % 12 == 0) 12 else initialHour % 12
    var hourIndex by remember { mutableStateOf(initialH12 - 1) }        // 0..11 → 1..12
    var minuteIndex by remember { mutableStateOf(initialMinute) }       // 0..59
    var periodIndex by remember { mutableStateOf(if (initialHour >= 12) 1 else 0) }

    val hours = remember { (1..12).map { it.toString() } }
    val minutes = remember { (0..59).map { "%02d".format(it) } }
    val periods = remember { listOf("AM", "PM") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set reminder time", fontFamily = Caprasimo, color = GgInk) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WheelColumn(
                    items = hours,
                    selectedIndex = hourIndex,
                    onSelected = { hourIndex = it },
                    width = 56.dp,
                )
                Text(
                    ":",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = GgInkMuted,
                )
                WheelColumn(
                    items = minutes,
                    selectedIndex = minuteIndex,
                    onSelected = { minuteIndex = it },
                    width = 56.dp,
                )
                Spacer(Modifier.width(8.dp))
                WheelColumn(
                    items = periods,
                    selectedIndex = periodIndex,
                    onSelected = { periodIndex = it },
                    width = 60.dp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val h12 = hourIndex + 1
                val hour24 = when {
                    periodIndex == 0 -> if (h12 == 12) 0 else h12          // AM
                    else -> if (h12 == 12) 12 else h12 + 12                // PM
                }
                onConfirm(hour24, minuteIndex)
            }) {
                Text("Set", color = GgPrimaryDeep, fontFamily = Nunito, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GgInkSoft, fontFamily = Nunito)
            }
        },
    )
}

private val WheelItemHeight = 44.dp
private const val WheelVisibleCount = 5

/**
 * A single snapping scroll wheel (iOS-style). The center row is highlighted; the
 * item nearest the viewport center is the selection, reported when scrolling settles.
 */
@Composable
private fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    width: Dp,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    // Index of the item currently closest to the vertical center of the viewport.
    val centerIndex by remember {
        derivedStateOf {
            val layout = state.layoutInfo
            if (layout.visibleItemsInfo.isEmpty()) {
                selectedIndex
            } else {
                val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                layout.visibleItemsInfo.minByOrNull {
                    abs((it.offset + it.size / 2f) - viewportCenter)
                }?.index ?: selectedIndex
            }
        }
    }

    // Commit the selection once the wheel comes to rest on a new value.
    LaunchedEffect(centerIndex, state.isScrollInProgress) {
        if (!state.isScrollInProgress && centerIndex != selectedIndex) onSelected(centerIndex)
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(WheelItemHeight * WheelVisibleCount),
        contentAlignment = Alignment.Center,
    ) {
        // Selection band behind the center row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelItemHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(GgMoss),
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = WheelItemHeight * (WheelVisibleCount / 2)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items.size) { i ->
                val isSelected = i == centerIndex
                Box(
                    modifier = Modifier
                        .height(WheelItemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = items[i],
                        fontFamily = Nunito,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        fontSize = if (isSelected) 22.sp else 18.sp,
                        color = if (isSelected) GgPrimaryDeep else GgInkMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
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
        Text(text = value, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = GgInk)
        Text(text = label, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = GgInkMuted)
    }
}

/** How many streak freezes are on hand, and where more come from. */
@Composable
private fun FreezeCard(freezes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(GgFrostLight),
            contentAlignment = Alignment.Center,
        ) {
            GgIcon(name = GgIconName.Snowflake, color = GgFrost, size = 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Streak freezes",
                fontFamily = Nunito,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = GgInk,
            )
            Text(
                text = "A missed day is forgiven. One free each month; more in the Shop.",
                fontFamily = Nunito,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = GgInkSoft,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "$freezes/$MAX_STREAK_FREEZES",
            fontFamily = Nunito,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            color = GgFrost,
        )
    }
}

/**
 * Shown on "Log out" while journal changes are still queued. Once a "Sync now" empties the
 * queue it says so and offers a plain log out.
 */
@Composable
private fun UnsyncedLogOutDialog(
    unsynced: Int,
    syncing: Boolean,
    syncFailed: Boolean,
    onSyncNow: () -> Unit,
    onLogOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val thoughts = if (unsynced == 1) "1 thought hasn't" else "$unsynced thoughts haven't"
    val message = when {
        syncing -> "Syncing…"
        unsynced == 0 -> "Everything you wrote is safe in the garden."
        syncFailed -> "Still can't reach the garden. $thoughts synced yet. " +
            "Logging out now deletes ${if (unsynced == 1) "it" else "them"} from this phone for good."
        else -> "$thoughts synced yet. Logging out now deletes ${if (unsynced == 1) "it" else "them"} " +
            "from this phone for good."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (unsynced == 0) "All synced" else "Not synced yet", fontFamily = Caprasimo, color = GgInk)
        },
        text = { Text(message, fontFamily = Nunito, color = GgInkSoft, modifier = Modifier.testTag("unsynced_logout_message")) },
        confirmButton = {
            if (unsynced == 0) {
                TextButton(onClick = onLogOut) {
                    Text("Log out", color = GgPrimary, fontFamily = Nunito, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onSyncNow, enabled = !syncing) {
                    Text("Sync now", color = GgPrimary, fontFamily = Nunito, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (unsynced == 0) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = GgInkSoft, fontFamily = Nunito) }
            } else {
                TextButton(onClick = onLogOut, enabled = !syncing) {
                    Text("Log out anyway", color = GgAccent, fontFamily = Nunito)
                }
            }
        },
    )
}

/** How far through the current level, and what the next one takes. */
@Composable
private fun LevelBar(progress: LevelProgress) {
    val fraction by animateFloatAsState(
        targetValue = progress.fraction.coerceIn(0f, 1f), animationSpec = tween(600), label = "xp",
    )
    Box(
        modifier = Modifier.width(180.dp).height(6.dp).clip(CircleShape).background(GgMoss),
    ) {
        Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(CircleShape).background(GgPrimary))
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = "${progress.xpIntoLevel} / ${progress.xpForNext} XP to level ${progress.level + 1}",
        fontFamily = Nunito,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        color = GgInkMuted,
    )
}
