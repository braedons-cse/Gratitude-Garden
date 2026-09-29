package com.gratitudegarden.app.ui.components

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.gratitudegarden.app.data.PhotoPreparer
import com.gratitudegarden.app.ui.sprites.GgIcon
import com.gratitudegarden.app.ui.sprites.GgIconName
import com.gratitudegarden.app.ui.theme.GgInk
import com.gratitudegarden.app.ui.theme.GgInkMuted
import com.gratitudegarden.app.ui.theme.GgMoss
import com.gratitudegarden.app.ui.theme.GgPrimary
import com.gratitudegarden.app.ui.theme.Nunito
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

// ── Entry photos (roadmap 1.4) ───────────────────────────────────────
// Shared by the new-entry sheet and the Journal's edit dialog.

/**
 * A photo picked for an entry and not saved yet, like the text typed beside it. Prepared off
 * the main thread into the staging folder; [handOver] passes the file on to be saved, and
 * whatever is still the draft's own when the dialog closes is deleted.
 */
@Stable
class PhotoDraft internal constructor(
    private val prepare: suspend (Uri) -> File,
    private val scope: CoroutineScope,
) {
    var staged by mutableStateOf<File?>(null)
        private set
    var preparing by mutableStateOf(false)
        private set

    /** The last pick couldn't be read as an image. Cleared by the next pick. */
    var failed by mutableStateOf(false)
        private set

    // Once handed over, [staged] is still shown (the sheet stays open while it plants) but
    // is no longer the draft's to delete.
    private var handedOver = false

    fun use(uri: Uri, afterwards: () -> Unit = {}) {
        preparing = true
        failed = false
        scope.launch {
            try {
                val file = prepare(uri)
                staged?.delete()
                staged = file
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            } finally {
                preparing = false
                afterwards()
            }
        }
    }

    fun drop() {
        if (!handedOver) staged?.delete()
        staged = null
        handedOver = false
    }

    /** The staged file, from now on the caller's to save or delete. It stays on show. */
    fun handOver(): File? = staged.also { handedOver = it != null }
}

@Composable
fun rememberPhotoDraft(prepare: suspend (Uri) -> File): PhotoDraft {
    val scope = rememberCoroutineScope()
    val draft = remember { PhotoDraft(prepare, scope) }
    DisposableEffect(draft) { onDispose { draft.drop() } }
    return draft
}

/**
 * "Add a photo" (from the gallery or the camera), or the photo about to be saved with a way
 * to take it off. The system photo picker needs no permission, and the camera app takes the
 * picture itself, so neither asks for one.
 */
@Composable
fun PhotoPickerRow(
    draft: PhotoDraft,
    /** Whether the entry will have a photo: a staged one, or its own. */
    hasPhoto: Boolean,
    /** That photo's file, when there is one to draw; null shows a placeholder. */
    shown: File?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    /** False while the entry is being saved: the photo is on its way and can't change now. */
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    var choosing by remember { mutableStateOf(false) }
    // Survives the trip to the camera app, which may recreate the activity.
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { draft.use(it) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = capturePath?.let(::File)
        capturePath = null
        if (file == null) return@rememberLauncherForActivityResult
        if (saved && file.length() > 0) draft.use(Uri.fromFile(file)) { file.delete() } else file.delete()
    }

    fun openCamera() {
        val file = PhotoPreparer.newCaptureFile(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
        capturePath = file.path
        try {
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            capturePath = null
            file.delete()
        }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            draft.preparing -> Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GgPrimary, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
            }
            hasPhoto -> {
                Box(Modifier.size(72.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GgMoss.copy(alpha = 0.35f))
                            .testTag("entry_photo_thumbnail"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (shown != null) {
                            AsyncImage(
                                model = shown,
                                contentDescription = "Photo for this thought",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            GgIcon(name = GgIconName.Photo, color = GgInkMuted, size = 24.dp, contentDescription = "Photo for this thought")
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable(enabled = enabled, onClickLabel = "Remove photo", onClick = onRemove)
                            .testTag("entry_photo_remove"),
                        contentAlignment = Alignment.Center,
                    ) {
                        GgIcon(name = GgIconName.Close, color = Color.White, size = 14.dp, contentDescription = "Remove photo")
                    }
                }
                if (enabled) PhotoAction("Replace", GgIconName.Photo) { choosing = true }
            }
            enabled -> PhotoAction("Add a photo", GgIconName.Photo, Modifier.testTag("entry_photo_add")) { choosing = true }
        }
        if (draft.failed) {
            Text("Couldn't use that photo.", fontFamily = Nunito, fontSize = 12.sp, color = GgInkMuted)
        }
    }

    if (choosing) {
        Dialog(onDismissRequest = { choosing = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(8.dp),
            ) {
                ChoiceRow("Choose from gallery", GgIconName.Photo) {
                    choosing = false
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                ChoiceRow("Take a photo", GgIconName.Camera) {
                    choosing = false
                    openCamera()
                }
            }
        }
    }
}

@Composable
private fun PhotoAction(label: String, icon: GgIconName, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GgIcon(name = icon, color = GgPrimary, size = 18.dp)
        Text(label, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GgInk)
    }
}

@Composable
private fun ChoiceRow(label: String, icon: GgIconName, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GgIcon(name = icon, color = GgPrimary, size = 20.dp)
        Text(label, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GgInk)
    }
}

/**
 * An entry's photo in the Journal, cropped to 4:3. [load] finds or fetches the file; until it
 * has one (offline, and never downloaded here) a quiet placeholder holds the space.
 */
@Composable
fun EntryPhoto(path: String, load: suspend (String) -> File?, onOpen: (File) -> Unit, modifier: Modifier = Modifier) {
    val file by produceState<File?>(null, path) { value = load(path) }
    val loaded = file
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .background(GgMoss.copy(alpha = 0.35f))
            .then(if (loaded != null) Modifier.clickable(onClickLabel = "View photo") { onOpen(loaded) } else Modifier)
            .testTag("entry_photo"),
        contentAlignment = Alignment.Center,
    ) {
        if (loaded != null) {
            AsyncImage(
                model = loaded,
                contentDescription = "Photo with this thought",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            GgIcon(name = GgIconName.Photo, color = GgInkMuted, size = 28.dp, contentDescription = "Photo not downloaded yet")
        }
    }
}

/** A photo filling the screen; a tap anywhere closes it. */
@Composable
fun PhotoViewer(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClickLabel = "Close photo", onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = file,
                contentDescription = "Photo with this thought",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
