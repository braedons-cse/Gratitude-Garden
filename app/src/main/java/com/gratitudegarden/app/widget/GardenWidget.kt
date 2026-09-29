package com.gratitudegarden.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.runtime.collectAsState
import com.gratitudegarden.app.GratitudeGardenApplication
import com.gratitudegarden.app.MainActivity
import com.gratitudegarden.app.R
import com.gratitudegarden.app.ui.theme.GgBgSage
import com.gratitudegarden.app.ui.theme.GgInk
import com.gratitudegarden.app.ui.theme.GgInkSoft
import com.gratitudegarden.app.ui.theme.GgPrimary
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * The home-screen widget: the garden as it stands, the streak, what's left of today, and a
 * way in to write. It reads [WidgetSync.state], which the app keeps current, so it shows the
 * device's copy offline as well as on.
 *
 * Three layouts by its actual size (launcher cells vary a lot): narrow is the streak and
 * Write; wide puts the garden beside them, as tall as the widget; tall puts it above. The
 * garden is one bitmap ([GardenSnapshot]) whichever layouts show it.
 */
class GardenWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sync = (context.applicationContext as GratitudeGardenApplication).container.widgetSync
        sync.start()
        WidgetMidnight.arm(context)
        // A first frame with the garden in it, if it can be read quickly (a cold process).
        withTimeoutOrNull(2.seconds) { sync.state.filterNotNull().first() }

        provideContent {
            val state by sync.state.collectAsState()
            WidgetContent(state)
        }
    }

    /** The picker's preview on Android 15+ (see [publishPreviews]): a garden a few weeks in. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        // A preview is composed at no particular size; this is a typical 4x2 cell.
        provideContent {
            CompositionLocalProvider(LocalSize provides DpSize(350.dp, 170.dp)) {
                WidgetContent(PreviewState)
            }
        }
    }

    companion object {
        /** Narrower than this, there's no room for the garden beside the words. */
        val MIN_WIDE_WIDTH = 180.dp

        /** Taller than this (and wide), the garden goes above them instead. */
        val MIN_TALL_HEIGHT = 220.dp
    }
}

private val PreviewState = WidgetState.Ready(
    streakDays = 12,
    streakHeld = false,
    thoughtsLeft = 7,
    backdropSlug = "backdrop.cherry_grove",
    gridRows = 6,
    gridCols = 5,
    plants = listOf(
        WidgetPlant(1, 1, "seed.sunset_tulip", 3),
        WidgetPlant(3, 1, "seed.field_daisy", 2),
        WidgetPlant(0, 2, "seed.big_sunflower", 3),
        WidgetPlant(2, 3, "seed.sleepy_lavender", 3),
        WidgetPlant(4, 3, "seed.sunset_tulip", 1),
        WidgetPlant(1, 4, "seed.field_daisy", 3),
    ),
)

private val Padding = 12.dp

private val Ink = ColorProvider(GgInk)
private val InkSoft = ColorProvider(GgInkSoft)
private val Primary = ColorProvider(GgPrimary)
private val OnPrimary = ColorProvider(Color.White)

@Composable
private fun WidgetContent(state: WidgetState?) {
    val context = LocalContext.current
    val size = LocalSize.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ColorProvider(GgBgSage))
            .cornerRadius(20.dp)
            .clickable(actionStartActivity(openAppIntent(context)))
            .padding(Padding),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            null -> Message("Your garden")
            WidgetState.SignedOut -> Message("Sign in to grow your garden")
            is WidgetState.Ready -> when {
                size.width < GardenWidget.MIN_WIDE_WIDTH -> Small(state)
                size.height >= GardenWidget.MIN_TALL_HEIGHT -> Tall(state)
                // The card is taller than wide: as tall as the widget's inner height allows.
                else -> Wide(state, gardenWidth = (size.height - Padding * 2) / GardenSnapshot.aspect(state.gridRows, state.gridCols))
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = TextStyle(color = InkSoft, fontSize = 14.sp))
}

@Composable
private fun Small(state: WidgetState.Ready) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Streak(state)
        Spacer(GlanceModifier.height(4.dp))
        ThoughtsLeft(state)
        Spacer(GlanceModifier.height(10.dp))
        WriteButton()
    }
}

@Composable
private fun Wide(state: WidgetState.Ready, gardenWidth: Dp) {
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        GardenImage(state, GlanceModifier.fillMaxHeight().width(gardenWidth))
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Streak(state)
            Spacer(GlanceModifier.height(2.dp))
            ThoughtsLeft(state)
            Spacer(GlanceModifier.height(8.dp))
            WriteButton()
        }
    }
}

@Composable
private fun Tall(state: WidgetState.Ready) {
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        GardenImage(state, GlanceModifier.fillMaxWidth().defaultWeight())
        Spacer(GlanceModifier.height(10.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Streak(state)
                ThoughtsLeft(state)
            }
            WriteButton()
        }
    }
}

@Composable
private fun GardenImage(state: WidgetState.Ready, modifier: GlanceModifier) {
    val plants = state.plants.size
    Image(
        provider = ImageProvider(SnapshotCache.bitmapFor(state)),
        contentDescription = if (plants == 1) "Your garden: 1 plant" else "Your garden: $plants plants",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/**
 * The last garden painted. Exact sizing composes once per size the launcher reports
 * (portrait and landscape at least); they all get this one bitmap, which the platform then
 * sends once rather than once per layout.
 */
private object SnapshotCache {
    private var state: WidgetState.Ready? = null
    private var bitmap: Bitmap? = null

    @Synchronized
    fun bitmapFor(state: WidgetState.Ready): Bitmap {
        bitmap?.takeIf { state == this.state }?.let { return it }
        return GardenSnapshot.render(state).also {
            this.state = state
            bitmap = it
        }
    }
}

@Composable
private fun Streak(state: WidgetState.Ready) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(if (state.streakHeld) R.drawable.ic_widget_snowflake else R.drawable.ic_widget_flame),
            contentDescription = null,
            modifier = GlanceModifier.size(18.dp),
        )
        Spacer(GlanceModifier.width(4.dp))
        val days = if (state.streakDays == 1) "1 day" else "${state.streakDays} days"
        Text(days, style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun ThoughtsLeft(state: WidgetState.Ready) {
    val text = when (state.thoughtsLeft) {
        0 -> "All of today's thoughts planted"
        1 -> "1 thought left today"
        else -> "${state.thoughtsLeft} thoughts left today"
    }
    Text(text, style = TextStyle(color = InkSoft, fontSize = 12.sp), maxLines = 2)
}

@Composable
private fun WriteButton() {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .background(Primary)
            .cornerRadius(18.dp)
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .clickable(actionStartActivity(MainActivity.writeIntent(context))),
        contentAlignment = Alignment.Center,
    ) {
        Text("Write", style = TextStyle(color = OnPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold))
    }
}

private fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
