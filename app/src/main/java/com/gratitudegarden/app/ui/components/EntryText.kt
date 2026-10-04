package com.gratitudegarden.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratitudegarden.app.ui.theme.GgInk
import com.gratitudegarden.app.ui.theme.GgInkMuted
import com.gratitudegarden.app.ui.theme.GgPrimary
import com.gratitudegarden.app.ui.theme.GgPrimaryDeep
import com.gratitudegarden.app.ui.theme.Nunito
import com.gratitudegarden.app.util.EntryLine
import com.gratitudegarden.app.util.continueList
import com.gratitudegarden.app.util.entryLines
import com.gratitudegarden.app.util.toggleBullet

// ── Entry text with lists (roadmap 1.4) ──────────────────────────────
// Shared by the new-entry sheet and the Journal, like EntryMood and EntryPhotos. The rules
// for what a list is live in util/EntryLists.kt.

/** An entry's text, list items shown as bullets under a hanging indent. */
@Composable
fun EntryText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val lines = remember(text) { entryLines(text) }
    if (lines.none { it is EntryLine.Item }) {
        Text(text, style = style, modifier = modifier)
        return
    }
    Column(modifier) {
        lines.forEach { line ->
            when (line) {
                is EntryLine.Plain -> Text(line.text, style = style)
                is EntryLine.Item -> Row {
                    // Screen readers read the item; the bullet is only a mark.
                    Text("•", style = style, modifier = Modifier.width(16.dp).clearAndSetSemantics {})
                    Text(line.text, style = style, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Where an entry is written or edited: grows with the text up to a point, then scrolls.
 * Enter at the end of a list item starts the next one, and the list button under it turns
 * the current line into an item or back.
 */
@Composable
fun EntryTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Lands on the inner BasicTextField, so UI tests can type into it directly.
    testTag: String? = null,
) {
    val style = TextStyle(fontFamily = Nunito, fontSize = 15.sp, color = GgInk)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp, max = 240.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(14.dp),
        ) {
            if (value.text.isEmpty()) Text(placeholder, style = style.copy(color = GgInkMuted))
            BasicTextField(
                value = value,
                onValueChange = { next ->
                    val edit = if (next.selection.collapsed && next.text != value.text) {
                        continueList(value.text, next.text, next.selection.end)
                    } else null
                    onValueChange(edit?.let { TextFieldValue(it.text, TextRange(it.cursor)) } ?: next)
                },
                enabled = enabled,
                textStyle = style,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                cursorBrush = SolidColor(GgPrimary),
                // The whole white box takes a tap, not just the lines written so far.
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .let { if (testTag == null) it else it.testTag(testTag) },
            )
        }
        Text(
            "• List",
            fontFamily = Nunito,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            color = GgPrimaryDeep,
            modifier = Modifier
                .align(Alignment.End)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(RoundedCornerShape(999.dp))
                .clickable(enabled = enabled, onClickLabel = "Make this line a list item, or a plain line again") {
                    val edit = toggleBullet(value.text, value.selection.end)
                    onValueChange(TextFieldValue(edit.text, TextRange(edit.cursor)))
                }
                .background(Color.White.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
