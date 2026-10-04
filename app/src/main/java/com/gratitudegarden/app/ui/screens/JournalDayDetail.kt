package com.gratitudegarden.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gratitudegarden.app.data.GratitudeEntry
import com.gratitudegarden.app.ui.components.PillButton
import com.gratitudegarden.app.ui.theme.Caprasimo
import com.gratitudegarden.app.ui.theme.GgBgSage
import com.gratitudegarden.app.ui.theme.GgInkMuted
import com.gratitudegarden.app.ui.theme.GgInkSoft
import com.gratitudegarden.app.ui.theme.GgPrimaryDeep
import com.gratitudegarden.app.ui.theme.Nunito
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── One day in the Journal (roadmap 1.4) ─────────────────────────────
// Opened from a section header or a day in the week strip. The list already groups by day;
// this is the day on its own, oldest thought first, read in the order it was written.

private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")

/**
 * [entries] is that day's thoughts, kept live, so an edit or delete made from here shows at
 * once. [thoughtsLeft] only matters for today, where it offers "Write another".
 */
@Composable
internal fun DayDetailDialog(
    day: LocalDate,
    entries: Flow<List<GratitudeEntry>>,
    thoughtsLeft: Int,
    onEntryClick: (GratitudeEntry) -> Unit,
    onWrite: () -> Unit,
    loadPhoto: suspend (String) -> File?,
    onOpenPhoto: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows by entries.collectAsStateWithLifecycle(initialValue = null)
    val isToday = remember(day) { day == LocalDate.now() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(GgBgSage)
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
                .testTag("day_detail"),
        ) {
            Text(
                if (isToday) "Today" else day.format(dayFmt),
                fontFamily = Caprasimo,
                fontSize = 20.sp,
                color = GgPrimaryDeep,
            )
            rows?.let { list ->
                Text(
                    when (list.size) {
                        0 -> "No thoughts on this day."
                        1 -> "1 thought"
                        else -> "${list.size} thoughts"
                    },
                    fontFamily = Nunito,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = GgInkSoft,
                )
            }
            Spacer(Modifier.height(12.dp))
            rows.orEmpty().forEach { entry ->
                EntryCard(
                    entry = entry,
                    onClick = { onEntryClick(entry) },
                    loadPhoto = loadPhoto,
                    onOpenPhoto = onOpenPhoto,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (isToday && thoughtsLeft > 0) {
                Spacer(Modifier.height(8.dp))
                PillButton(
                    text = "Write another",
                    onClick = onWrite,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "day_write_another",
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Close",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = GgInkMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onDismiss).padding(8.dp),
            )
        }
    }
}
