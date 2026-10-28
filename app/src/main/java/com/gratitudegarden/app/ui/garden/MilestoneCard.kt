package com.gratitudegarden.app.ui.garden

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.gratitudegarden.app.R
import com.gratitudegarden.app.ui.theme.GgBgSage
import com.gratitudegarden.app.ui.theme.GgInkSoft
import com.gratitudegarden.app.ui.theme.GgPrimary
import com.gratitudegarden.app.ui.theme.GgPrimaryDeep
import com.gratitudegarden.app.util.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The milestone as a picture to share: the dialog's last frame over the count and a
 * wordmark. Nothing the user wrote is on it: no journal text, and no name.
 *
 * Painted off screen like the widget's garden ([com.gratitudegarden.app.widget.GardenSnapshot]);
 * the text goes through the platform canvas, since Compose text needs a composition.
 */
object MilestoneCard {
    /** 4:5, the portrait shape most apps show uncropped. */
    const val WIDTH = 1080
    const val HEIGHT = 1350

    private const val MARGIN = 60f
    private const val STAGE_CORNER = 48f

    /** The only file shared, overwritten each time; the folder is in res/xml/file_paths.xml. */
    private const val SHARE_DIR = "share"
    private const val SHARE_FILE = "milestone.png"

    fun render(context: Context, days: Int, backdropSlug: String?): Bitmap {
        val style = milestoneStyle(days)
        val image = ImageBitmap(WIDTH, HEIGHT)
        val w = WIDTH.toFloat()
        val stage = w - 2 * MARGIN
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w, HEIGHT.toFloat())) {
            drawRect(GgBgSage)
            val card = Path().apply {
                addRoundRect(RoundRect(MARGIN, MARGIN, MARGIN + stage, MARGIN + stage, CornerRadius(STAGE_CORNER)))
            }
            clipPath(card) {
                inset(MARGIN, MARGIN, MARGIN, size.height - MARGIN - stage) {
                    drawMilestoneStage(style, backdropSlug)
                }
            }

            val caprasimo = ResourcesCompat.getFont(context, R.font.caprasimo_regular) ?: Typeface.DEFAULT
            val nunito = Typeface.create(ResourcesCompat.getFont(context, R.font.nunito_variable), 650, false)
            fun paint(typeface: Typeface, px: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = px
                this.color = color
                textAlign = Paint.Align.CENTER
            }
            drawIntoCanvas { c ->
                val n = c.nativeCanvas
                val x = w / 2f
                n.drawText("$days days", x, 1140f, paint(caprasimo, 110f, GgPrimaryDeep.toArgb()))
                n.drawText("of gratitude", x, 1205f, paint(nunito, 44f, GgInkSoft.toArgb()))
                n.drawText("Gratitude Garden", x, 1298f, paint(caprasimo, 34f, GgPrimary.toArgb()))
            }
        }
        return image.asAndroidBitmap()
    }

    /** Render the card to the share folder and open the system share sheet with it. */
    suspend fun share(context: Context, days: Int, backdropSlug: String?) {
        val file = withContext(Dispatchers.Default) {
            val bitmap = render(context, days, backdropSlug)
            val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
            File(dir, SHARE_FILE).also { f ->
                f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            // ClipData too, or the chooser's own preview can't read the image.
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null)
        // A dialog's context wraps the activity; anything else needs a task of its own.
        val activity = context.findActivity()
        if (activity != null) activity.startActivity(chooser)
        else context.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
