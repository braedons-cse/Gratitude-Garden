package com.gratitudegarden.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gratitudegarden.app.data.GratitudeEntry
import com.gratitudegarden.app.data.local.SyncState
import com.gratitudegarden.app.util.LogComposableLifecycle
import com.gratitudegarden.app.util.LogTags
import com.gratitudegarden.app.ui.components.PillButton
import com.gratitudegarden.app.ui.journal.JournalUiState
import com.gratitudegarden.app.ui.journal.JournalViewModel
import com.gratitudegarden.app.ui.sprites.CoinIcon
import com.gratitudegarden.app.ui.sprites.MaturePlant
import com.gratitudegarden.app.ui.sprites.GgIcon
import com.gratitudegarden.app.ui.sprites.GgIconName
import com.gratitudegarden.app.ui.sprites.PlantPalette
import com.gratitudegarden.app.ui.theme.Caprasimo
import com.gratitudegarden.app.ui.theme.Nunito
import com.gratitudegarden.app.ui.theme.GgAccent
import com.gratitudegarden.app.ui.theme.GgAccentDeep
import com.gratitudegarden.app.ui.theme.GgBgSage
import com.gratitudegarden.app.ui.theme.GgInk
import com.gratitudegarden.app.ui.theme.GgInkMuted
import com.gratitudegarden.app.ui.theme.GgInkSoft
import com.gratitudegarden.app.ui.theme.GgMoss
import com.gratitudegarden.app.ui.theme.GgPrimary
import com.gratitudegarden.app.ui.theme.GgPrimaryDeep
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun JournalRoute() {
    LogComposableLifecycle(LogTags.JOURNAL_SCREEN)
    val vm: JournalViewModel = viewModel(factory = JournalViewModel.Factory)
    val ui by vm.ui.collectAsStateWithLifecycle()
    JournalScreen(
        ui = ui,
        onEdit = vm::edit,
        onDelete = vm::delete,
        onRefresh = vm::refresh,
        onLoadMore = vm::loadMore,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    ui: JournalUiState,
    onEdit: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
) {
    var actionEntry by remember { mutableStateOf<GratitudeEntry?>(null) }
    var editEntry by remember { mutableStateOf<GratitudeEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<GratitudeEntry?>(null) }

    // Ask the ViewModel for more of the history as the user nears the end of the list.
    // Only visible rows are ever composed (LazyColumn), and the ViewModel holds only
    // as many entries as have been scrolled to (a growing limit on the Room query).
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 3) onLoadMore()
            }
    }

    PullToRefreshBox(
        isRefreshing = ui.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().background(GgBgSage),
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        item(key = "header") {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Journal", fontFamily = Caprasimo, fontSize = 26.sp, color = GgPrimaryDeep)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                        .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GgIcon(name = GgIconName.Flame, color = GgAccent, size = 16.dp)
                    Spacer(Modifier.size(5.dp))
                    Text("${ui.streak}", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = GgInk)
                    Spacer(Modifier.size(3.dp))
                    Text("days", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = GgInkMuted)
                }
            }

            Spacer(Modifier.height(6.dp))
            Row {
                Text("${ui.totalEntries}", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = GgPrimaryDeep)
                Text(" kind thoughts planted so far.", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = GgInkSoft)
            }

            Spacer(Modifier.height(12.dp))
            WeekStrip(entryDates = ui.entryDates)
            Spacer(Modifier.height(14.dp))
        }

        if (!ui.loading && ui.sections.isEmpty()) {
            item(key = "empty") { EmptyJournal() }
        }

        ui.sections.forEach { section ->
            item(key = "section-${section.label}") {
                Text(
                    text = section.label.uppercase(),
                    fontFamily = Nunito,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = GgInkMuted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                )
            }
            items(section.entries, key = { it.id }) { entry ->
                EntryCard(entry = entry, onClick = { actionEntry = entry })
                Spacer(Modifier.height(8.dp))
            }
        }

        item(key = "footer") { Spacer(Modifier.height(20.dp)) }
    }
    }

    // Action menu
    actionEntry?.let { entry ->
        ActionDialog(
            onEdit = { actionEntry = null; editEntry = entry },
            onDelete = { actionEntry = null; deleteEntry = entry },
            onDismiss = { actionEntry = null },
        )
    }

    editEntry?.let { entry ->
        EditDialog(
            initial = entry.entryText,
            onSave = { text -> onEdit(entry.id, text); editEntry = null },
            onDismiss = { editEntry = null },
        )
    }

    deleteEntry?.let { entry ->
        ConfirmDeleteDialog(
            onConfirm = { onDelete(entry.id); deleteEntry = null },
            onDismiss = { deleteEntry = null },
        )
    }
}

@Composable
private fun WeekStrip(entryDates: Set<String>) {
    val today = remember { LocalDate.now() }
    val days = remember(entryDates) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(vertical = 12.dp, horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        days.forEach { day ->
            val on = entryDates.contains(day.toString())
            val isToday = day == today
            val dayName = day.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            val dayLabel = (if (isToday) "Today, $dayName" else dayName) +
                if (on) ", entry logged" else ", no entry"
            // Merge the weekday letter + dot into one spoken label so TalkBack reads
            // "Monday, entry logged" instead of just the letter "M".
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clearAndSetSemantics { contentDescription = dayLabel },
            ) {
                Text(
                    text = day.dayOfWeek.name.take(1),
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (isToday) GgPrimaryDeep else GgInkMuted,
                )
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(if (on) GgPrimary else GgBgSage),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) GgIcon(name = GgIconName.Check, color = Color(0xFFFAF5E8), size = 14.dp)
                }
            }
        }
    }
}

@Composable
private fun EntryCard(entry: GratitudeEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(GgMoss),
            contentAlignment = Alignment.Center,
        ) {
            MaturePlant(colors = PlantPalette.forSeed(entry.id), size = 40.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = entry.entryText,
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
                color = GgInk,
                lineHeight = 20.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = timeOf(entry.createdAt),
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = GgInkMuted,
                )
                Box(Modifier.size(3.dp).clip(CircleShape).background(GgInkMuted))
                if (entry.inputMethod == "voice_to_text") {
                    GgIcon(name = GgIconName.Mic, color = GgPrimary, size = 12.dp)
                }
                when (entry.syncState) {
                    // The server decides the reward, so there's no amount until it answers.
                    SyncState.PENDING -> SyncNote("waiting to sync", GgInkMuted)
                    SyncState.FAILED -> SyncNote("couldn't sync", GgAccentDeep)
                    SyncState.SYNCED -> {
                        CoinIcon(size = 12.dp)
                        Text(
                            text = "+${entry.coinsAwarded ?: 0}",
                            fontFamily = Nunito,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = GgPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncNote(text: String, color: Color) {
    Text(
        text = text,
        fontFamily = Nunito,
        fontWeight = FontWeight.Bold,
        fontSize = 11.5.sp,
        color = color,
        modifier = Modifier.testTag("entry_sync_note"),
    )
}

@Composable
private fun EmptyJournal() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MaturePlant(colors = PlantPalette.Daisy, size = 56.dp)
        Text("No thoughts yet", fontFamily = Caprasimo, fontSize = 20.sp, color = GgPrimaryDeep)
        Text(
            "Head to the Garden and plant your first kind thought.",
            fontFamily = Nunito,
            fontWeight = FontWeight.Medium,
            fontSize = 13.5.sp,
            color = GgInkSoft,
        )
    }
}

// ── Dialogs ──────────────────────────────────────────────────────
@Composable
private fun ActionDialog(onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(GgBgSage).padding(8.dp),
        ) {
            DialogRow("Edit thought", onEdit)
            DialogRow("Delete thought", onDelete, danger = true)
            DialogRow("Cancel", onDismiss, muted = true)
        }
    }
}

@Composable
private fun DialogRow(label: String, onClick: () -> Unit, danger: Boolean = false, muted: Boolean = false) {
    Text(
        text = label,
        fontFamily = Nunito,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = when {
            danger -> Color(0xFFB3261E)
            muted -> GgInkMuted
            else -> GgInk
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun EditDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(GgBgSage).padding(20.dp),
        ) {
            Text("Edit thought", fontFamily = Caprasimo, fontSize = 20.sp, color = GgPrimaryDeep)
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(fontFamily = Nunito, fontSize = 15.sp, color = GgInk),
                    cursorBrush = SolidColor(GgPrimary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            PillButton(text = "Save", onClick = { onSave(text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                "Cancel",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = GgInkMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onDismiss).padding(8.dp),
            )
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(GgBgSage).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Delete this thought?", fontFamily = Caprasimo, fontSize = 20.sp, color = GgPrimaryDeep)
            Spacer(Modifier.height(8.dp))
            Text(
                "This removes it from your journal for good.",
                fontFamily = Nunito,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                color = GgInkSoft,
            )
            Spacer(Modifier.height(18.dp))
            PillButton(text = "Delete", onClick = onConfirm, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                "Keep it",
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = GgInkMuted,
                modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
            )
        }
    }
}

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/**
 * Wall-clock time of an entry in [zone]. `created_at` arrives from Postgres in UTC, and
 * `OffsetDateTime.toLocalTime()` keeps that offset, so it must be moved to the device's zone
 * first. The zone is a parameter so tests don't depend on where they run.
 */
internal fun timeOf(createdAt: String, zone: ZoneId = ZoneId.systemDefault()): String = try {
    OffsetDateTime.parse(createdAt).atZoneSameInstant(zone).format(timeFmt)
} catch (_: Exception) {
    ""
}
