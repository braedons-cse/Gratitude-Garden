package com.cse5236.gratitudegarden.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cse5236.gratitudegarden.ui.components.PillButton
import com.cse5236.gratitudegarden.util.LogComposableLifecycle
import com.cse5236.gratitudegarden.util.LogTags
import com.cse5236.gratitudegarden.data.GardenPlantRow
import com.cse5236.gratitudegarden.ui.garden.GardenUiState
import com.cse5236.gratitudegarden.ui.garden.GardenViewModel
import com.cse5236.gratitudegarden.ui.sprites.CoinIcon
import com.cse5236.gratitudegarden.ui.sprites.PgIcon
import com.cse5236.gratitudegarden.ui.sprites.PgIconName
import com.cse5236.gratitudegarden.ui.sprites.Plant
import com.cse5236.gratitudegarden.ui.sprites.PlantPalette
import com.cse5236.gratitudegarden.ui.sprites.growthStageToSprite
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
import java.util.Locale

private val SoilTop = Color(0xFFA48560)
private val SoilBottom = Color(0xFF8B6F47)

@Composable
fun GardenRoute() {
    LogComposableLifecycle(LogTags.GARDEN_SCREEN)
    val vm: GardenViewModel = viewModel(factory = GardenViewModel.Factory)
    val ui by vm.ui.collectAsStateWithLifecycle()
    GardenScreen(
        ui = ui,
        onSubmit = { text, voice -> vm.submit(text, voice) },
        onWater = vm::water,
        onMessageShown = vm::consumeMessage,
        onReminderPromptDecided = vm::onReminderPromptDecided,
    )
}

@Composable
fun GardenScreen(
    ui: GardenUiState,
    onSubmit: (String, Boolean) -> Unit,
    onWater: (String) -> Unit,
    onMessageShown: () -> Unit,
    onReminderPromptDecided: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }
    var selectedPlant by remember { mutableStateOf<GardenPlantRow?>(null) }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onMessageShown()
        }
    }

    // Close the sheet once a submit finishes (submitting flips true → false).
    LaunchedEffect(ui.submitting) {
        if (!ui.submitting) showSheet = false
    }

    val title = if (ui.displayName.isNotBlank()) "${ui.displayName}'s Garden" else ui.gardenName

    Column(
        modifier = Modifier.fillMaxSize().background(PgBgSage).padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))

        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Chip {
                PgIcon(name = PgIconName.Flame, color = PgAccent, size = 16.dp)
                Spacer(Modifier.size(5.dp))
                Text("${ui.streak}", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = PgInk)
                Spacer(Modifier.size(3.dp))
                Text("days", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = PgInkMuted)
            }
            Text(
                text = title,
                fontFamily = Caprasimo,
                fontSize = 21.sp,
                color = PgPrimaryDeep,
                letterSpacing = (-0.3).sp,
            )
            Chip {
                CoinIcon(size = 16.dp)
                Spacer(Modifier.size(5.dp))
                Text("${ui.coins}", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = PgInk)
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
                Box(Modifier.size(6.dp).clip(CircleShape).background(PgAccent))
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "${ui.thoughtsLeft} thoughts left today",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = PgInkSoft,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Soil grid
        GardenGrid(ui = ui, onPlantClick = { selectedPlant = it })

        Spacer(Modifier.weight(1f))

        // Charge dots
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(ui.dailyCap) { i ->
                val filled = i < ui.thoughtsLeft
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (filled) PgPrimary else Color.Black.copy(alpha = 0.08f)),
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
            color = PgInkSoft,
        )
        Spacer(Modifier.height(18.dp))
    }

    if (showSheet) {
        NewEntrySheet(
            submitting = ui.submitting,
            onDismiss = { showSheet = false },
            onSubmit = onSubmit,
        )
    }

    selectedPlant?.let { plant ->
        PlantDetailDialog(
            plant = plant,
            slug = ui.itemSlugs[plant.itemId],
            coins = ui.coins,
            watering = ui.watering,
            onWater = { onWater(plant.id); selectedPlant = null },
            onDismiss = { selectedPlant = null },
        )
    }

    if (ui.showNotifPrompt) {
        NotifPromptDialog(
            onEnable = { onReminderPromptDecided(true) },
            onDecline = { onReminderPromptDecided(false) },
        )
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
                .background(PgBgSage)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(PgMoss),
                contentAlignment = Alignment.Center,
            ) {
                PgIcon(name = PgIconName.Flame, color = PgAccent, size = 34.dp)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Keep your streak alive 🌱",
                fontFamily = Caprasimo,
                fontSize = 22.sp,
                color = PgPrimaryDeep,
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
                color = PgInkSoft,
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
                color = PgInkMuted,
                modifier = Modifier.clickable(onClick = onDecline).padding(8.dp),
            )
        }
    }
}

@Composable
private fun Chip(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.7f))
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun GardenGrid(ui: GardenUiState, onPlantClick: (GardenPlantRow) -> Unit) {
    val plantAt = remember(ui.plants) { ui.plants.associateBy { it.gridY to it.gridX } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(SoilTop, SoilBottom)))
            .padding(10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().aspectRatio(ui.gridCols.toFloat() / ui.gridRows),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (r in 0 until ui.gridRows) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until ui.gridCols) {
                        val plant = plantAt[r to c]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.linearGradient(listOf(SoilTop, SoilBottom)))
                                .then(
                                    if (plant != null) Modifier.clickable { onPlantClick(plant) }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (plant != null) {
                                Plant(
                                    colors = PlantPalette.forSlug(ui.itemSlugs[plant.itemId]),
                                    stage = growthStageToSprite(plant.growthStage),
                                    modifier = Modifier.fillMaxSize().padding(4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MicButton(progress: Float, onClick: () -> Unit) {
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
                color = PgPrimary,
                startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(pad, pad), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(PgPrimary, PgPrimaryDeep)))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PgIcon(name = PgIconName.Mic, color = PgBgCream, size = 40.dp)
        }
    }
}

// ── New entry sheet (voice + text) ───────────────────────────────
@Composable
private fun NewEntrySheet(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var isVoice by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

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
        if (granted) startListening() else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(PgBgSage)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Share a kind thought", fontFamily = Caprasimo, fontSize = 22.sp, color = PgPrimaryDeep)
            Spacer(Modifier.height(16.dp))

            // Mic
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(
                        if (listening) Brush.verticalGradient(listOf(PgAccent, Color(0xFFD88040)))
                        else Brush.verticalGradient(listOf(PgPrimary, PgPrimaryDeep))
                    )
                    .clickable { onMicTap() },
                contentAlignment = Alignment.Center,
            ) {
                PgIcon(name = PgIconName.Mic, color = PgBgCream, size = 34.dp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = hint ?: "Tap the mic to speak, or type below",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = PgInkMuted,
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
                        color = PgInkMuted,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; isVoice = false },
                    textStyle = TextStyle(fontFamily = Nunito, fontSize = 15.sp, color = PgInk),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    cursorBrush = SolidColor(PgPrimary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(18.dp))

            PillButton(
                text = if (submitting) "Planting…" else "Plant it",
                onClick = { onSubmit(text, isVoice) },
                enabled = text.isNotBlank() && !submitting,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Cancel",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = PgInkMuted,
                modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
            )
        }
    }
}

private fun Modifier.heightInThought(): Modifier = this.height(96.dp)

// ── Plant detail / watering ──────────────────────────────────────
@Composable
private fun PlantDetailDialog(
    plant: GardenPlantRow,
    slug: String?,
    coins: Int,
    watering: Boolean,
    onWater: () -> Unit,
    onDismiss: () -> Unit,
) {
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
                .background(PgBgSage)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)).background(PgMoss),
                contentAlignment = Alignment.Center,
            ) {
                Plant(
                    colors = PlantPalette.forSlug(slug),
                    stage = growthStageToSprite(plant.growthStage),
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(plantNameFromSlug(slug), fontFamily = Caprasimo, fontSize = 22.sp, color = PgPrimaryDeep)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$stageLabel · ${plant.health.replaceFirstChar { it.uppercase() }}",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = PgInkSoft,
            )
            Spacer(Modifier.height(18.dp))

            if (isMature) {
                Text("Fully grown 🌼", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PgPrimary)
            } else {
                PillButton(
                    text = if (watering) "Watering…" else "Water · 10 coins",
                    onClick = onWater,
                    enabled = coins >= 10 && !watering,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (coins < 10) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Plant kind thoughts to earn coins to water.",
                        fontFamily = Nunito,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp,
                        color = PgInkMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Close",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = PgInkMuted,
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
