package com.gratitudegarden.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratitudegarden.app.model.Mood
import com.gratitudegarden.app.ui.theme.GgInk
import com.gratitudegarden.app.ui.theme.GgInkMuted
import com.gratitudegarden.app.ui.theme.GgInkSoft
import com.gratitudegarden.app.ui.theme.GgPrimaryDeep
import com.gratitudegarden.app.ui.theme.Nunito

// ── Entry moods (roadmap 1.4) ────────────────────────────────────────
// Shared by the new-entry sheet and the Journal's edit dialog, like EntryPhotos.

/** Cool to warm, from the palette's frost through moss to its accent. */
private fun Mood.tint(): Color = when (this) {
    Mood.ROUGH -> Color(0xFFB4CDE0)
    Mood.LOW -> Color(0xFFCBD9D6)
    Mood.OKAY -> Color(0xFFC9D9B8)
    Mood.GOOD -> Color(0xFFA9CB8E)
    Mood.GREAT -> Color(0xFFF6BE85)
}

/**
 * A face for [mood]: the mouth goes from a frown at rough, through a flat line at okay, to a
 * wide smile at great. Decorative; whatever shows it says the mood in words.
 */
@Composable
fun MoodFace(mood: Mood, size: Dp, modifier: Modifier = Modifier) {
    val fill = mood.tint()
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawCircle(fill)
        drawCircle(GgInk.copy(alpha = 0.18f), style = Stroke(w * 0.04f))
        val eye = w * 0.065f
        drawCircle(GgInk, eye, Offset(w * 0.35f, w * 0.40f))
        drawCircle(GgInk, eye, Offset(w * 0.65f, w * 0.40f))
        // -1 at rough, +1 at great: how far the mouth's middle sits below its corners.
        val curve = (mood.score - 3) / 2f
        val corners = w * (0.66f - curve * 0.04f)
        val mouth = Path().apply {
            moveTo(w * 0.31f, corners)
            quadraticTo(w * 0.5f, corners + curve * w * 0.2f, w * 0.69f, corners)
        }
        drawPath(mouth, GgInk, style = Stroke(width = w * 0.07f, cap = StrokeCap.Round))
    }
}

/**
 * "How are you feeling?" and the five faces. Optional: nothing is picked until one is
 * tapped, and tapping the picked one again takes it off. [selected] is a score, 1–5.
 */
@Composable
fun MoodPicker(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    /** False while the entry is being saved. */
    enabled: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("How are you feeling?", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GgInkSoft)
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Mood.entries.forEach { mood ->
                val isSelected = selected == mood.score
                Column(
                    modifier = Modifier
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(if (isSelected) null else mood.score) },
                        )
                        .semantics { contentDescription = "Mood: ${mood.label}" }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("entry_mood_${mood.score}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // With one picked, the rest step back.
                    val faded = selected != null && !isSelected
                    MoodFace(
                        mood = mood,
                        size = 40.dp,
                        modifier = Modifier
                            .alpha(if (faded) 0.45f else 1f)
                            .then(if (isSelected) Modifier.border(2.5.dp, GgPrimaryDeep, CircleShape) else Modifier),
                    )
                    Text(
                        mood.label,
                        fontFamily = Nunito,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = if (isSelected) GgInk else GgInkMuted,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

/** An entry's mood in the Journal's meta line: a small face and its word, read as one phrase. */
@Composable
fun MoodTag(mood: Mood, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = "Feeling ${mood.label}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MoodFace(mood = mood, size = 14.dp)
        Text(mood.label, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = GgInkMuted)
    }
}
