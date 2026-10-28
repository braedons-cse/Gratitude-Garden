package com.gratitudegarden.app.ui.garden

import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gratitudegarden.app.ui.components.PillButton
import com.gratitudegarden.app.ui.sprites.PlantColors
import com.gratitudegarden.app.ui.sprites.PlantPalette
import com.gratitudegarden.app.ui.sprites.drawBackdrop
import com.gratitudegarden.app.ui.sprites.drawPlant
import com.gratitudegarden.app.ui.theme.Caprasimo
import com.gratitudegarden.app.ui.theme.GgAccent
import com.gratitudegarden.app.ui.theme.GgBgSage
import com.gratitudegarden.app.ui.theme.GgInkMuted
import com.gratitudegarden.app.ui.theme.GgInkSoft
import com.gratitudegarden.app.ui.theme.GgMoss
import com.gratitudegarden.app.ui.theme.GgPrimaryDeep
import com.gratitudegarden.app.ui.theme.Nunito
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** How one milestone looks and reads. Shared by the dialog and the share card. */
internal data class MilestoneStyle(
    val days: Int,
    val title: String,
    val line: String,
    val plant: PlantColors,
    /** Smaller plants around the main one; the garden fills out as the streak grows. */
    val companions: List<PlantColors>,
)

internal fun milestoneStyle(days: Int): MilestoneStyle = when {
    days >= 100 -> MilestoneStyle(
        days, "100 days of gratitude", "A hundred days in a row. Look what you've grown.",
        PlantPalette.Rose,
        listOf(PlantPalette.Daisy, PlantPalette.Lavender, PlantPalette.Sunflower, PlantPalette.Tulip),
    )
    days >= 30 -> MilestoneStyle(
        days, "A month of gratitude", "Thirty days of small, good things. That's a habit now.",
        PlantPalette.Sunflower,
        listOf(PlantPalette.Lavender, PlantPalette.Daisy),
    )
    else -> MilestoneStyle(
        days, "A week of gratitude", "Seven days in a row. Your garden noticed.",
        PlantPalette.Tulip,
        emptyList(),
    )
}

// Mirror SoilTop / SoilBottom in GardenScreen.
private val SoilTop = Color(0xFFA48560)
private val SoilBottom = Color(0xFF8B6F47)

/** The top of the stage that the backdrop takes; the rest is soil. */
private const val BAND = 0.36f
private const val PETALS = 24

/** Where the companions sit, as fractions of the stage, and how big. */
private val CompanionSpots = listOf(
    Offset(0.16f, 0.58f), Offset(0.84f, 0.58f), Offset(0.2f, 0.88f), Offset(0.8f, 0.88f),
)
private const val COMPANION_SIZE = 0.2f
private val PlantCenter = Offset(0.5f, 0.68f)
private const val PLANT_SIZE = 0.46f

/**
 * The milestone scene, filling a square draw area: the backdrop over a patch of soil, the
 * milestone's plant in the middle and its companions around it, and the petals it threw.
 * The dialog animates it; the share card paints its last frame.
 *
 * @param growth 0–3: sprout, sapling, then in bloom, each stage popping in.
 * @param burst 0–1: the petals flying out and settling.
 * @param sway degrees the plants lean in the breeze once grown.
 */
internal fun DrawScope.drawMilestoneStage(
    style: MilestoneStyle,
    backdropSlug: String?,
    growth: Float = 3f,
    burst: Float = 1f,
    sway: Float = 0f,
) {
    val w = size.width
    val h = size.height
    val band = h * BAND
    clipRect(bottom = band) { inset(0f, 0f, 0f, h - band) { drawBackdrop(backdropSlug) } }
    drawRect(
        Brush.verticalGradient(listOf(SoilTop, SoilBottom), startY = band, endY = h),
        topLeft = Offset(0f, band),
        size = Size(w, h - band),
    )

    // Companions are already there when the main plant blooms, so they pop in during its last stage.
    val companionsIn = ((growth - 2f) / 1f).coerceIn(0f, 1f)
    style.companions.zip(CompanionSpots).forEachIndexed { i, (colors, spot) ->
        val t = ((companionsIn - i * 0.15f) / 0.55f).coerceIn(0f, 1f)
        if (t > 0f) plantAt(colors, 3, Offset(spot.x * w, spot.y * h), COMPANION_SIZE * w, easeOutBack(t), -sway)
    }

    val stage = growth.coerceIn(0f, 3f)
    if (stage > 0f) {
        val sprite = stage.toInt().coerceIn(0, 2) + 1
        val t = if (stage >= 3f) 1f else stage - stage.toInt()
        plantAt(style.plant, sprite, Offset(PlantCenter.x * w, PlantCenter.y * h), PLANT_SIZE * w, 0.6f + 0.4f * easeOutBack(t), sway)
    }

    if (burst > 0f) drawPetals(style, Offset(PlantCenter.x * w, PlantCenter.y * h), burst)
}

private fun DrawScope.plantAt(colors: PlantColors, sprite: Int, center: Offset, side: Float, scale: Float, sway: Float) {
    translate(center.x - side / 2f, center.y - side / 2f) {
        rotate(sway, pivot = Offset(side / 2f, side / 2f)) {
            scale(scale, pivot = Offset(side / 2f, side / 2f)) {
                // drawPlant fills its draw area, so give it a side × side one.
                inset(0f, 0f, size.width - side, size.height - side) { drawPlant(colors, sprite) }
            }
        }
    }
}

/** Petals thrown from [origin]: out fast, then drifting down a little. The same every frame. */
private fun DrawScope.drawPetals(style: MilestoneStyle, origin: Offset, burst: Float) {
    val s = size.minDimension
    val colors = listOfNotNull(style.plant.bloom, GgAccent, GgMoss, style.plant.center)
    val random = Random(style.days)
    val out = 1f - (1f - burst) * (1f - burst) * (1f - burst)
    repeat(PETALS) { i ->
        val angle = (i.toFloat() / PETALS + random.nextFloat() * 0.03f) * 2f * PI.toFloat()
        val reach = s * (0.22f + random.nextFloat() * 0.26f)
        val spin = random.nextFloat() * 360f
        val color = colors[i % colors.size]
        val petal = s * (0.022f + random.nextFloat() * 0.014f)
        val at = Offset(
            origin.x + cos(angle) * reach * out,
            origin.y + sin(angle) * reach * out + s * 0.06f * burst * burst,
        )
        rotate(spin + burst * 140f, pivot = at) {
            drawOval(
                color = color.copy(alpha = (burst * 5f).coerceAtMost(1f) * 0.92f),
                topLeft = Offset(at.x - petal, at.y - petal * 0.55f),
                size = Size(petal * 2f, petal * 1.1f),
            )
        }
    }
}

private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

/** [t]'s position within [start]..[end], clamped to 0–1. */
private fun segment(t: Float, start: Float, end: Float): Float = ((t - start) / (end - start)).coerceIn(0f, 1f)

/** False when the system's animator duration scale is 0 ("Remove animations"). */
@Composable
private fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/** -1 to 1 and back, forever: the grown plants leaning in a breeze. */
@Composable
private fun breeze(): Float {
    val transition = rememberInfiniteTransition(label = "milestoneBreeze")
    val wave by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "milestoneWave",
    )
    return wave
}

private const val TIMELINE_MILLIS = 2400

/**
 * The 7 / 30 / 100-day moment: the plant grows and throws its petals, the count runs up,
 * and Share sends a picture of it. [onClose] marks it seen.
 */
@Composable
fun MilestoneCelebration(
    milestone: Milestone,
    backdropSlug: String?,
    onClose: () -> Unit,
    animate: Boolean = animationsEnabled(),
) {
    val style = remember(milestone.days) { milestoneStyle(milestone.days) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }

    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) progress.animateTo(1f, tween(TIMELINE_MILLIS, easing = LinearEasing))
    }
    // Fixed for the dialog's life, so the call is always made or never.
    val wave = if (animate) breeze() else 0f

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(GgBgSage)
                .padding(20.dp)
                .testTag("milestone_dialog"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val t = progress.value
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                drawMilestoneStage(
                    style,
                    backdropSlug,
                    growth = 3f * segment(t, 0f, 0.6f),
                    burst = segment(t, 0.55f, 1f),
                    sway = wave * 3.5f * segment(t, 0.85f, 1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "${(milestone.days * segment(t, 0.1f, 0.6f)).roundToInt()} days",
                fontFamily = Caprasimo,
                fontSize = 40.sp,
                color = GgPrimaryDeep,
            )
            Text(
                style.title,
                fontFamily = Caprasimo,
                fontSize = 20.sp,
                color = GgPrimaryDeep,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                style.line,
                fontFamily = Nunito,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                color = GgInkSoft,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            PillButton(
                text = if (sharing) "Getting it ready…" else "Share",
                enabled = !sharing,
                onClick = {
                    sharing = true
                    scope.launch {
                        try {
                            MilestoneCard.share(context, milestone.days, backdropSlug)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Couldn't share that. Please try again.", Toast.LENGTH_SHORT).show()
                        } finally {
                            sharing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "milestone_share",
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Keep growing",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = GgInkMuted,
                modifier = Modifier.clickable(onClick = onClose).padding(8.dp),
            )
        }
    }
}
