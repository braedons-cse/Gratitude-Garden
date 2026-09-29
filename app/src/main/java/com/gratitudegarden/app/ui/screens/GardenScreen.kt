package com.gratitudegarden.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gratitudegarden.app.ui.components.PhotoPickerRow
import com.gratitudegarden.app.ui.components.PillButton
import com.gratitudegarden.app.ui.components.rememberPhotoDraft
import com.gratitudegarden.app.util.LogComposableLifecycle
import com.gratitudegarden.app.util.LogTags
import com.gratitudegarden.app.util.findActivity
import com.gratitudegarden.app.data.GardenPlantRow
import com.gratitudegarden.app.data.Item
import com.gratitudegarden.app.ui.garden.GardenUiState
import com.gratitudegarden.app.ui.garden.GardenViewModel
import com.gratitudegarden.app.ui.garden.LevelUp
import com.gratitudegarden.app.ui.sprites.BackdropScene
import com.gratitudegarden.app.ui.sprites.CoinIcon
import com.gratitudegarden.app.ui.sprites.GgIcon
import com.gratitudegarden.app.ui.sprites.GgIconName
import com.gratitudegarden.app.ui.sprites.Plant
import com.gratitudegarden.app.ui.sprites.PlantPalette
import com.gratitudegarden.app.ui.sprites.growthStageToSprite
import com.gratitudegarden.app.ui.theme.Caprasimo
import com.gratitudegarden.app.ui.theme.Nunito
import com.gratitudegarden.app.ui.theme.GgAccent
import com.gratitudegarden.app.ui.theme.GgAccentDeep
import com.gratitudegarden.app.ui.theme.GgBgCream
import com.gratitudegarden.app.ui.theme.GgBgSage
import com.gratitudegarden.app.ui.theme.GgFrost
import com.gratitudegarden.app.ui.theme.GgInk
import com.gratitudegarden.app.ui.theme.GgInkMuted
import com.gratitudegarden.app.ui.theme.GgInkSoft
import com.gratitudegarden.app.ui.theme.GgMoss
import com.gratitudegarden.app.ui.theme.GgPrimary
import com.gratitudegarden.app.ui.theme.GgPrimaryDeep
import com.gratitudegarden.app.ui.theme.pressScale
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

private val SoilTop = Color(0xFFA48560)
private val SoilBottom = Color(0xFF8B6F47)

/**
 * The backdrop scene above the plot, at most. The screen doesn't scroll, so the band only
 * gets what the plot leaves: about 55dp for a 6x5 grid on a 411x914dp phone.
 */
private val BackdropBandHeight = 72.dp
private val BackdropBandMinHeight = 28.dp

@Composable
fun GardenRoute(
    placingItemId: String? = null,
    onPlacementDone: () -> Unit = {},
    onDragActive: (Boolean) -> Unit = {},
    onOpenShop: () -> Unit = {},
    openEntrySheet: Boolean = false,
    onEntrySheetOpened: () -> Unit = {},
) {
    LogComposableLifecycle(LogTags.GARDEN_SCREEN)
    val vm: GardenViewModel = viewModel(factory = GardenViewModel.Factory)
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Blank for the moment before Room's first read, instead of "My Garden, 0 coins".
    if (ui.loading) return
    GardenScreen(
        ui = ui,
        onSubmit = { text, voice, photo -> vm.submit(text, voice, photo) },
        preparePhoto = vm::preparePhoto,
        onWater = vm::water,
        onMessageShown = vm::consumeMessage,
        onReminderPromptDecided = vm::onReminderPromptDecided,
        onLevelUpClosed = { visitShop ->
            vm.onLevelUpSeen()
            if (visitShop) onOpenShop()
        },
        placingItemId = placingItemId,
        onPlantSeed = { itemId, x, y -> vm.plantSeedAt(itemId, x, y) },
        onMovePlant = { plantId, x, y -> vm.movePlant(plantId, x, y) },
        onDigUp = vm::digUp,
        onNeedsConnection = vm::onNeedsConnection,
        onPlacementDone = onPlacementDone,
        onDragActive = onDragActive,
        openEntrySheet = openEntrySheet,
        onEntrySheetOpened = onEntrySheetOpened,
    )
}

@Composable
fun GardenScreen(
    ui: GardenUiState,
    onSubmit: (text: String, voice: Boolean, photo: File?) -> Unit,
    preparePhoto: suspend (Uri) -> File = { error("no photo preparer") },
    onWater: (String) -> Unit,
    onMessageShown: () -> Unit,
    onReminderPromptDecided: (Boolean) -> Unit = {},
    /** True when the user chose to go to the shop. */
    onLevelUpClosed: (visitShop: Boolean) -> Unit = {},
    placingItemId: String? = null,
    onPlantSeed: (String, Int, Int) -> Unit = { _, _, _ -> },
    onMovePlant: (String, Int, Int) -> Unit = { _, _, _ -> },
    onDigUp: (String) -> Unit = {},
    onNeedsConnection: () -> Unit = {},
    onPlacementDone: () -> Unit = {},
    onDragActive: (Boolean) -> Unit = {},
    /** A Write link (widget, notification) asked for the entry sheet. */
    openEntrySheet: Boolean = false,
    onEntrySheetOpened: () -> Unit = {},
) {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }
    var selectedPlant by remember { mutableStateOf<GardenPlantRow?>(null) }
    // A cell the user tapped to plant into (garden-initiated seed picker).
    var pickerCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onMessageShown()
        }
    }

    // Close the sheet once a submit finishes (submitting flips true → false). Only on the
    // flip: a sheet opened on arrival (a Write link) must survive the first composition.
    val submitting by rememberUpdatedState(ui.submitting)
    LaunchedEffect(Unit) {
        snapshotFlow { submitting }.drop(1).filter { !it }.collect { showSheet = false }
    }

    LaunchedEffect(openEntrySheet) {
        if (openEntrySheet) {
            showSheet = true
            onEntrySheetOpened()
        }
    }

    val title = if (ui.displayName.isNotBlank()) "${ui.displayName}'s Garden" else ui.gardenName

    // Roll the counters up to their values instead of snapping.
    val animatedCoins by animateIntAsState(
        targetValue = ui.coins, animationSpec = tween(600), label = "coins",
    )
    val animatedStreak by animateIntAsState(
        targetValue = ui.streak, animationSpec = tween(600), label = "streak",
    )

    Column(
        modifier = Modifier.fillMaxSize().background(GgBgSage).padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))

        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StreakChip(days = animatedStreak, held = ui.streakHeld)
            Text(
                text = title,
                fontFamily = Caprasimo,
                fontSize = 21.sp,
                color = GgPrimaryDeep,
                letterSpacing = (-0.3).sp,
            )
            Chip {
                CoinIcon(size = 16.dp)
                Spacer(Modifier.size(5.dp))
                Text("$animatedCoins", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = GgInk)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Thoughts-left chip
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(GgAccent))
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "${ui.thoughtsLeft} thoughts left today",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = GgInkSoft,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (!ui.online) {
            OfflineBanner()
            Spacer(Modifier.height(8.dp))
        }

        // Placement banner — shown when the Shop sent us here to place a seed.
        if (placingItemId != null) {
            val name = ui.ownedSeeds.firstOrNull { it.id == placingItemId }?.name ?: "seed"
            PlacementBanner(seedName = name, onCancel = onPlacementDone)
            Spacer(Modifier.height(8.dp))
        }

        // Soil grid. It takes the room left above the charge dots, which the backdrop band
        // grows into; the plot keeps its size and sits at the top.
        Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 10.dp)) {
            GardenGrid(
                ui = ui,
                placing = placingItemId != null,
                onPlantClick = { selectedPlant = it },
                onEmptyClick = { x, y ->
                    val pid = placingItemId
                    if (!ui.online) {
                        onNeedsConnection()
                    } else if (pid != null) {
                        onPlantSeed(pid, x, y)
                        onPlacementDone()
                    } else {
                        pickerCell = x to y
                    }
                },
                onMovePlant = onMovePlant,
                onDragActive = onDragActive,
            )
        }

        // Charge dots
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(ui.dailyCap) { i ->
                val filled = i < ui.thoughtsLeft
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (filled) GgPrimary else Color.Black.copy(alpha = 0.08f)),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        MicButton(
            progress = if (ui.dailyCap == 0) 0f else ui.thoughtsLeft.toFloat() / ui.dailyCap,
            onClick = { showSheet = true },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Tap to share a kind thought",
            fontFamily = Nunito,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
            color = GgInkSoft,
        )
        Spacer(Modifier.height(18.dp))
    }

    if (showSheet) {
        NewEntrySheet(
            submitting = ui.submitting,
            onDismiss = { showSheet = false },
            onSubmit = onSubmit,
            preparePhoto = preparePhoto,
        )
    }

    selectedPlant?.let { plant ->
        PlantDetailDialog(
            plant = plant,
            slug = ui.itemSlugs[plant.itemId],
            coins = ui.coins,
            watering = ui.watering,
            online = ui.online,
            onWater = { onWater(plant.id); selectedPlant = null },
            onDigUp = { onDigUp(plant.id); selectedPlant = null },
            onDismiss = { selectedPlant = null },
        )
    }

    pickerCell?.let { (x, y) ->
        SeedPickerSheet(
            seeds = ui.ownedSeeds,
            onPick = { item -> onPlantSeed(item.id, x, y); pickerCell = null },
            onDismiss = { pickerCell = null },
        )
    }

    // One dialog at a time; the reminder prompt waits for the level-up to be closed.
    val levelUp = ui.levelUp
    if (levelUp != null) {
        LevelUpDialog(levelUp, onClose = onLevelUpClosed)
    } else if (ui.showNotifPrompt) {
        NotifPromptDialog(
            onEnable = { onReminderPromptDecided(true) },
            onDecline = { onReminderPromptDecided(false) },
        )
    }
}

@Composable
private fun LevelUpDialog(levelUp: LevelUp, onClose: (visitShop: Boolean) -> Unit) {
    val unlocked = levelUp.unlocked
    Dialog(onDismissRequest = { onClose(false) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(GgBgSage)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(GgMoss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${levelUp.level}",
                    fontFamily = Caprasimo,
                    fontSize = 30.sp,
                    color = GgPrimaryDeep,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Level ${levelUp.level}! 🌿",
                fontFamily = Caprasimo,
                fontSize = 22.sp,
                color = GgPrimaryDeep,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                when (unlocked.size) {
                    0 -> "Every day you write, your garden grows with you. Keep going."
                    1 -> "${unlocked[0]} is now in the shop."
                    else -> "${unlocked.dropLast(1).joinToString(", ")} and ${unlocked.last()} are now in the shop."
                },
                fontFamily = Nunito,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                color = GgInkSoft,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            if (unlocked.isNotEmpty()) {
                PillButton(
                    text = "Visit the shop",
                    onClick = { onClose(true) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Later",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = GgInkMuted,
                    modifier = Modifier.clickable { onClose(false) }.padding(8.dp),
                )
            } else {
                PillButton(
                    text = "Nice",
                    onClick = { onClose(false) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── One-time "enable daily reminders?" prompt (first gratitude entry) ──────
@Composable
private fun NotifPromptDialog(
    onEnable: () -> Unit,
    onDecline: () -> Unit,
) {
    val context = LocalContext.current

    // On Android 13+ we need the runtime grant before notifications can post. We
    // enable + schedule regardless of the user's choice here: the worker no-ops
    // without the grant, and the Me screen surfaces the "notifications off" state.
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> onEnable() }

    fun accept() {
        val needsGrant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsGrant) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else onEnable()
    }

    Dialog(onDismissRequest = onDecline) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(GgBgSage)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(GgMoss),
                contentAlignment = Alignment.Center,
            ) {
                GgIcon(name = GgIconName.Flame, color = GgAccent, size = 34.dp)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Keep your streak alive 🌱",
                fontFamily = Caprasimo,
                fontSize = 22.sp,
                color = GgPrimaryDeep,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Want a gentle daily reminder to type or speak a gratitude entry? " +
                    "We'll nudge you once a day so your streak keeps growing. You can " +
                    "change the time or turn it off anytime on the Me tab.",
                fontFamily = Nunito,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                color = GgInkSoft,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            PillButton(
                text = "Enable reminders",
                onClick = { accept() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Not now",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = GgInkMuted,
                modifier = Modifier.clickable(onClick = onDecline).padding(8.dp),
            )
        }
    }
}

@Composable
private fun Chip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.7f))
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** The streak count; a snowflake instead of the flame while freezes are holding it. */
@Composable
private fun StreakChip(days: Int, held: Boolean) {
    val label = if (held) "$days day streak, held by a streak freeze" else "$days day streak"
    Chip(modifier = Modifier.clearAndSetSemantics { contentDescription = label }) {
        if (held) GgIcon(name = GgIconName.Snowflake, color = GgFrost, size = 16.dp)
        else GgIcon(name = GgIconName.Flame, color = GgAccent, size = 16.dp)
        Spacer(Modifier.size(5.dp))
        Text("$days", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = GgInk)
        Spacer(Modifier.size(3.dp))
        Text("days", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = GgInkMuted)
    }
}

@Composable
private fun GardenGrid(
    ui: GardenUiState,
    placing: Boolean,
    onPlantClick: (GardenPlantRow) -> Unit,
    onEmptyClick: (Int, Int) -> Unit,
    onMovePlant: (String, Int, Int) -> Unit,
    onDragActive: (Boolean) -> Unit,
) {
    val plantAt = remember(ui.plants) { ui.plants.associateBy { it.gridY to it.gridX } }
    var cellPx by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(LocalDensity.current) { 4.dp.toPx() }
    // Row holding the plant being dragged — lifted above the others so the
    // dragged sprite is never occluded by a neighbouring row.
    var draggingRow by remember { mutableStateOf<Int?>(null) }

    // The equipped backdrop is a band above the plot, in the same card: scenery behind
    // the garden rather than under it, since the tiles would hide anything drawn beneath.
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
        // Up to the full band in whatever height the plot leaves; below the minimum it's
        // dropped rather than squashed (offline, the banner can take most of the room).
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = BackdropBandHeight)) {
            if (maxHeight >= BackdropBandMinHeight) {
                BackdropScene(
                    slug = ui.activeBackdropId?.let { ui.itemSlugs[it] },
                    modifier = Modifier.fillMaxWidth().height(maxHeight),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(SoilTop, SoilBottom)))
                .padding(10.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().aspectRatio(ui.gridCols.toFloat() / ui.gridRows),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (r in 0 until ui.gridRows) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .zIndex(if (draggingRow == r) 1f else 0f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (c in 0 until ui.gridCols) {
                            val plant = plantAt[r to c]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .onSizeChanged { if (it.width > 0 && it.height > 0) cellPx = it }
                                    .background(
                                        Brush.linearGradient(listOf(SoilTop, SoilBottom)),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .then(
                                        if (plant == null && placing)
                                            Modifier.border(2.dp, GgPrimary.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                                        else Modifier
                                    )
                                    .then(
                                        if (plant == null)
                                            Modifier
                                                .semantics {
                                                    contentDescription = "Empty plot, row ${r + 1}, column ${c + 1}"
                                                }
                                                .clickable(onClickLabel = "Plant here") { onEmptyClick(c, r) }
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (plant != null) {
                                    DraggablePlant(
                                        plant = plant,
                                        slug = ui.itemSlugs[plant.itemId],
                                        phaseMillis = (r * ui.gridCols + c) * 240,
                                        cellPx = cellPx,
                                        gapPx = gapPx,
                                        movable = ui.online,
                                        onTap = { onPlantClick(plant) },
                                        onDragStart = { draggingRow = r; onDragActive(true) },
                                        onDragStop = { draggingRow = null; onDragActive(false) },
                                        onDrop = { tx, ty ->
                                            val inBounds = tx in 0 until ui.gridCols && ty in 0 until ui.gridRows
                                            val occupied = plantAt[ty to tx] != null
                                            if (inBounds && !occupied) onMovePlant(plant.id, tx, ty)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A plant sitting in its soil cell. Tap opens its detail; long-press picks it up
 * to drag to another cell (moved in whole-cell steps relative to its start cell).
 * The drag uses a cheap graphicsLayer translation, and the caller disables the
 * pager while a plant is held (onDragStart/onDragStop) so the swipe doesn't fight
 * the drag.
 */
@Composable
private fun DraggablePlant(
    plant: GardenPlantRow,
    slug: String?,
    phaseMillis: Int,
    cellPx: IntSize,
    gapPx: Float,
    movable: Boolean,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDragStop: () -> Unit,
    onDrop: (Int, Int) -> Unit,
) {
    val strideX = cellPx.width + gapPx
    val strideY = cellPx.height + gapPx
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }

    val plantName = (slug ?: "plant").replace('_', ' ').replace('-', ' ')
        .replaceFirstChar { it.uppercase() }
    val plantStage = plant.growthStage.replace('_', ' ')
    // The tap is a pointerInput gesture (invisible to TalkBack), so expose an
    // explicit semantics label + click action here.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "$plantName, $plantStage"
                onClick(label = "Open plant details") { onTap(); true }
            }
            .zIndex(if (dragging) 10f else 0f)
            .graphicsLayer {
                translationX = drag.x
                translationY = drag.y
                if (dragging) {
                    scaleX = 1.15f; scaleY = 1.15f
                    shadowElevation = 16f
                }
            }
            .pointerInput(plant.id) {
                detectTapGestures { onTap() }
            }
            .pointerInput(plant.id, strideX, strideY, movable) {
                // Moving is an online-only action; offline a long press does nothing.
                if (!movable) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = true; onDragStart() },
                    onDrag = { change, delta -> drag += delta; change.consume() },
                    onDragEnd = {
                        val dx = if (strideX > 0f) (drag.x / strideX).roundToInt() else 0
                        val dy = if (strideY > 0f) (drag.y / strideY).roundToInt() else 0
                        dragging = false
                        drag = Offset.Zero
                        onDragStop()
                        if (dx != 0 || dy != 0) onDrop(plant.gridX + dx, plant.gridY + dy)
                    },
                    onDragCancel = { dragging = false; drag = Offset.Zero; onDragStop() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Plant(
            colors = PlantPalette.forSlug(slug),
            stage = growthStageToSprite(plant.growthStage),
            modifier = Modifier.fillMaxSize().padding(4.dp),
            idle = !dragging,
            phaseMillis = phaseMillis,
        )
    }
}

/**
 * Shown while the device is offline. The journal keeps working (entries queue and sync
 * later), so the banner says what still works, not just what doesn't. One line: the
 * Garden has no scroll, and every line here comes out of the soil grid's space.
 */
@Composable
private fun OfflineBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GgInk.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("offline_banner"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(GgInkMuted))
        Spacer(Modifier.size(8.dp))
        Text(
            text = "Offline · your thoughts will sync later",
            fontFamily = Nunito,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = GgInkSoft,
        )
    }
}

/** Banner shown while placing a seed from the Shop — tap a soil cell to plant. */
@Composable
private fun PlacementBanner(seedName: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GgMoss)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Tap a spot to plant your $seedName",
            fontFamily = Nunito,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = GgPrimaryDeep,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Cancel",
            fontFamily = Nunito,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = GgInkSoft,
            modifier = Modifier.clickable(onClick = onCancel).padding(start = 10.dp),
        )
    }
}

/** Sheet of the seeds you own, shown after tapping an empty plot in the garden. */
@Composable
private fun SeedPickerSheet(
    seeds: List<Item>,
    onPick: (Item) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(GgBgSage)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Plant a seed", fontFamily = Caprasimo, fontSize = 22.sp, color = GgPrimaryDeep)
            Spacer(Modifier.height(4.dp))
            if (seeds.isEmpty()) {
                Text(
                    "You don't own any seeds yet — visit the Shop to get some.",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    color = GgInkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Text(
                    "Choose one of your seeds to plant here.",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = GgInkSoft,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    seeds.forEach { seed ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .clickable { onPick(seed) }
                                .padding(12.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(GgMoss),
                                contentAlignment = Alignment.Center,
                            ) {
                                Plant(
                                    colors = PlantPalette.forSlug(seed.slug),
                                    stage = 3,
                                    modifier = Modifier.fillMaxSize().padding(10.dp),
                                )
                            }
                            Text(
                                seed.name,
                                fontFamily = Nunito,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = GgInk,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Cancel",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = GgInkMuted,
                modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
            )
        }
    }
}

@Composable
private fun MicButton(progress: Float, onClick: () -> Unit) {
    // Sweep the progress ring to its new value instead of snapping.
    val animProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "micProgress",
    )
    // Slow breathing pulse to invite a tap.
    val infinite = rememberInfiniteTransition(label = "micPulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "micPulseScale",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(modifier = Modifier.size(116.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val pad = stroke / 2 + 2.dp.toPx()
            val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
            drawArc(
                color = Color.White.copy(alpha = 0.6f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(pad, pad), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = GgPrimary,
                startAngle = -90f, sweepAngle = 360f * animProgress, useCenter = false,
                topLeft = Offset(pad, pad), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Box(
            modifier = Modifier
                .size(86.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .pressScale(interaction, pressedScale = 0.90f)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(GgPrimary, GgPrimaryDeep)))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClickLabel = "Record a gratitude note",
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            GgIcon(
                name = GgIconName.Mic,
                color = GgBgCream,
                size = 40.dp,
                contentDescription = "Record a gratitude note",
            )
        }
    }
}

// ── New entry sheet (voice + text + photo) ───────────────────────
// `internal` (not `private`) so the androidTest source set can drive it in isolation.
@Composable
internal fun NewEntrySheet(
    submitting: Boolean,
    onDismiss: () -> Unit,
    /** [photo] is handed over: from then on it's the receiver's to save or delete. */
    onSubmit: (text: String, voice: Boolean, photo: File?) -> Unit,
    preparePhoto: suspend (Uri) -> File,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val photo = rememberPhotoDraft(preparePhoto)
    var isVoice by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var micAsked by remember { mutableStateOf(false) }
    var showMicBlocked by remember { mutableStateOf(false) }

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }
    DisposableEffect(Unit) { onDispose { recognizer?.destroy() } }

    fun startListening() {
        val r = recognizer ?: run {
            hint = "Speech isn't available here — type your thought instead."
            return
        }
        listening = true
        hint = "Listening…"
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val phrase = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (phrase.isNotBlank()) {
                    text = phrase
                    isVoice = true
                }
                listening = false
                hint = null
            }
            override fun onError(error: Int) {
                listening = false
                hint = "Didn't catch that — try again or type it."
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        r.startListening(intent)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else hint = "Microphone off — you can type your thought instead."
    }

    fun onMicTap() {
        if (listening) {
            recognizer?.stopListening()
            listening = false
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            startListening()
            return
        }
        // Denied: prompt if we still can, otherwise it's blocked ("don't ask
        // again") so show a popup that offers to re-enable it in Settings.
        val activity = context.findActivity()
        val canPrompt = !micAsked ||
            (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO))
        if (canPrompt) {
            micAsked = true
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            showMicBlocked = true
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(GgBgSage)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Share a kind thought", fontFamily = Caprasimo, fontSize = 22.sp, color = GgPrimaryDeep)
            Spacer(Modifier.height(16.dp))

            // Mic
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(
                        if (listening) Brush.verticalGradient(listOf(GgAccent, Color(0xFFD88040)))
                        else Brush.verticalGradient(listOf(GgPrimary, GgPrimaryDeep))
                    )
                    .clickable(
                        onClickLabel = if (listening) "Stop recording" else "Start recording",
                    ) { onMicTap() },
                contentAlignment = Alignment.Center,
            ) {
                GgIcon(
                    name = GgIconName.Mic,
                    color = GgBgCream,
                    size = 34.dp,
                    contentDescription = if (listening) "Stop recording" else "Start recording",
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = hint ?: "Tap the mic to speak, or type below",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = GgInkMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            // Editable text (voice result lands here; also typed fallback)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightInThought()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(14.dp),
            ) {
                if (text.isEmpty()) {
                    Text(
                        "Something you're grateful for…",
                        fontFamily = Nunito,
                        fontSize = 15.sp,
                        color = GgInkMuted,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; isVoice = false },
                    textStyle = TextStyle(fontFamily = Nunito, fontSize = 15.sp, color = GgInk),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    cursorBrush = SolidColor(GgPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("new_entry_text_field"),
                )
            }

            Spacer(Modifier.height(12.dp))
            PhotoPickerRow(
                draft = photo,
                hasPhoto = photo.staged != null,
                shown = photo.staged,
                onRemove = photo::drop,
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting,
            )

            Spacer(Modifier.height(16.dp))

            PillButton(
                text = if (submitting) "Planting…" else "Plant it",
                onClick = { onSubmit(text, isVoice, photo.handOver()) },
                // Text is still what makes an entry; a photo only goes with one.
                enabled = text.isNotBlank() && !submitting && !photo.preparing,
                modifier = Modifier.fillMaxWidth(),
                testTag = "plant_it_button",
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Cancel",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = GgInkMuted,
                modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
            )
        }
    }

    if (showMicBlocked) {
        MicBlockedDialog(
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                )
                showMicBlocked = false
            },
            onDismiss = { showMicBlocked = false },
        )
    }
}

private fun Modifier.heightInThought(): Modifier = this.height(96.dp)

// Popup shown when the mic is tapped but the permission is blocked — offers a
// path back to re-enable it (or to just type instead).
@Composable
private fun MicBlockedDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(GgBgSage)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(GgMoss),
                contentAlignment = Alignment.Center,
            ) {
                GgIcon(name = GgIconName.Mic, color = GgInkMuted, size = 34.dp)
            }
            Spacer(Modifier.height(14.dp))
            Text("Microphone is off", fontFamily = Caprasimo, fontSize = 22.sp, color = GgPrimaryDeep)
            Spacer(Modifier.height(10.dp))
            Text(
                "To speak your gratitude, turn the microphone back on in Settings — " +
                    "or just type your thought below.",
                fontFamily = Nunito,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                color = GgInkSoft,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            PillButton(text = "Open Settings", onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                "Type instead",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = GgInkMuted,
                modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
            )
        }
    }
}

// ── Plant detail / watering ──────────────────────────────────────
@Composable
private fun PlantDetailDialog(
    plant: GardenPlantRow,
    slug: String?,
    coins: Int,
    watering: Boolean,
    online: Boolean,
    onWater: () -> Unit,
    onDigUp: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDig by remember { mutableStateOf(false) }
    val stage = plant.growthStage.lowercase()
    val stageLabel = when (stage) {
        "seedling" -> "Seedling"
        "sapling" -> "Sapling"
        else -> "Mature"
    }
    val isMature = stage == "mature"
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(GgBgSage)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)).background(GgMoss),
                contentAlignment = Alignment.Center,
            ) {
                Plant(
                    colors = PlantPalette.forSlug(slug),
                    stage = growthStageToSprite(plant.growthStage),
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(plantNameFromSlug(slug), fontFamily = Caprasimo, fontSize = 22.sp, color = GgPrimaryDeep)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$stageLabel · ${plant.health.replaceFirstChar { it.uppercase() }}",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = GgInkSoft,
            )
            Spacer(Modifier.height(18.dp))

            if (isMature) {
                Text("Fully grown 🌼", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GgPrimary)
            } else {
                PillButton(
                    text = when {
                        !online -> "Needs a connection"
                        watering -> "Watering…"
                        else -> "Water · 10 coins"
                    },
                    onClick = onWater,
                    enabled = online && coins >= 10 && !watering,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (online && coins < 10) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Plant kind thoughts to earn coins to water.",
                        fontFamily = Nunito,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp,
                        color = GgInkMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            // Dig up (destructive) — two-tap confirm since it's irreversible.
            Text(
                text = if (confirmDig) "Tap again to dig up" else "Dig up",
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = if (online) GgAccentDeep else GgInkMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(enabled = online) { if (confirmDig) onDigUp() else confirmDig = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Close",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = GgInkMuted,
                modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
            )
        }
    }
}

private fun plantNameFromSlug(slug: String?): String {
    val base = slug?.substringAfter('.', "") ?: ""
    if (base.isBlank()) return "Plant"
    return base.split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
