package com.cse5236.gratitudegarden.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cse5236.gratitudegarden.data.GratitudeEntry
import com.cse5236.gratitudegarden.util.LogComposableLifecycle
import com.cse5236.gratitudegarden.util.LogTags
import com.cse5236.gratitudegarden.ui.components.PillButton
import com.cse5236.gratitudegarden.ui.journal.JournalUiState
import com.cse5236.gratitudegarden.ui.journal.JournalViewModel
import com.cse5236.gratitudegarden.ui.sprites.CoinIcon
import com.cse5236.gratitudegarden.ui.sprites.MaturePlant
import com.cse5236.gratitudegarden.ui.sprites.PgIcon
import com.cse5236.gratitudegarden.ui.sprites.PgIconName
import com.cse5236.gratitudegarden.ui.sprites.PlantPalette
import com.cse5236.gratitudegarden.ui.theme.Caprasimo
import com.cse5236.gratitudegarden.ui.theme.Nunito
import com.cse5236.gratitudegarden.ui.theme.PgAccent
import com.cse5236.gratitudegarden.ui.theme.PgBgSage
import com.cse5236.gratitudegarden.ui.theme.PgInk
import com.cse5236.gratitudegarden.ui.theme.PgInkMuted
import com.cse5236.gratitudegarden.ui.theme.PgInkSoft
import com.cse5236.gratitudegarden.ui.theme.PgMoss
import com.cse5236.gratitudegarden.ui.theme.PgPrimary
import com.cse5236.gratitudegarden.ui.theme.PgPrimaryDeep
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun JournalRoute() {
    LogComposableLifecycle(LogTags.JOURNAL_SCREEN)
    val vm: JournalViewModel = viewModel(factory = JournalViewModel.Factory)
    val ui by vm.ui.collectAsStateWithLifecycle()
    JournalScreen(ui = ui, onEdit = vm::edit, onDelete = vm::delete, onRefresh = vm::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    ui: JournalUiState,
    onEdit: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit = {},
) {
    var actionEntry by remember { mutableStateOf<GratitudeEntry?>(null) }
    var editEntry by remember { mutableStateOf<GratitudeEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<GratitudeEntry?>(null) }

    PullToRefreshBox(
        isRefreshing = ui.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().background(PgBgSage),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Journal", fontFamily = Caprasimo, fontSize = 26.sp, color = PgPrimaryDeep)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.8f))
                    .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PgIcon(name = PgIconName.Flame, color = PgAccent, size = 16.dp)
                Spacer(Modifier.size(5.dp))
                Text("${ui.streak}", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = PgInk)
                Spacer(Modifier.size(3.dp))
                Text("days", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = PgInkMuted)
            }
        }

        Spacer(Modifier.height(6.dp))
        Row {
            Text("${ui.totalEntries}", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = PgPrimaryDeep)
            Text(" kind thoughts planted so far.", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = PgInkSoft)
        }

        Spacer(Modifier.height(12.dp))
        WeekStrip(entryDates = ui.entryDates)

        Spacer(Modifier.height(14.dp))

        if (!ui.loading && ui.sections.isEmpty()) {
            EmptyJournal()
        }

        ui.sections.forEach { section ->
            Text(
                text = section.label.uppercase(),
                fontFamily = Nunito,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = PgInkMuted,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
            section.entries.forEach { entry ->
                EntryCard(entry = entry, onClick = { actionEntry = entry })
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = day.dayOfWeek.name.take(1),
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (isToday) PgPrimaryDeep else PgInkMuted,
                )
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(if (on) PgPrimary else PgBgSage),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) PgIcon(name = PgIconName.Check, color = Color(0xFFFAF5E8), size = 14.dp)
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
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(PgMoss),
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
                color = PgInk,
                lineHeight = 20.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = timeOf(entry.createdAt),
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = PgInkMuted,
                )
                Box(Modifier.size(3.dp).clip(CircleShape).background(PgInkMuted))
                if (entry.inputMethod == "voice_to_text") {
                    PgIcon(name = PgIconName.Mic, color = PgPrimary, size = 12.dp)
                }
                CoinIcon(size = 12.dp)
                Text(
                    text = "+${entry.coinsAwarded}",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = PgPrimary,
                )
            }
        }
    }
}

@Composable
private fun EmptyJournal() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MaturePlant(colors = PlantPalette.Daisy, size = 56.dp)
        Text("No thoughts yet", fontFamily = Caprasimo, fontSize = 20.sp, color = PgPrimaryDeep)
        Text(
            "Head to the Garden and plant your first kind thought.",
            fontFamily = Nunito,
            fontWeight = FontWeight.Medium,
            fontSize = 13.5.sp,
            color = PgInkSoft,
        )
    }
}

// ── Dialogs ──────────────────────────────────────────────────────
@Composable
private fun ActionDialog(onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PgBgSage).padding(8.dp),
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
            muted -> PgInkMuted
            else -> PgInk
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun EditDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PgBgSage).padding(20.dp),
        ) {
            Text("Edit thought", fontFamily = Caprasimo, fontSize = 20.sp, color = PgPrimaryDeep)
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(fontFamily = Nunito, fontSize = 15.sp, color = PgInk),
                    cursorBrush = SolidColor(PgPrimary),
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
                color = PgInkMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onDismiss).padding(8.dp),
            )
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PgBgSage).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Delete this thought?", fontFamily = Caprasimo, fontSize = 20.sp, color = PgPrimaryDeep)
            Spacer(Modifier.height(8.dp))
            Text(
                "This removes it from your journal for good.",
                fontFamily = Nunito,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                color = PgInkSoft,
            )
            Spacer(Modifier.height(18.dp))
            PillButton(text = "Delete", onClick = onConfirm, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                "Keep it",
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = PgInkMuted,
                modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
            )
        }
    }
}

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun timeOf(createdAt: String): String = try {
    OffsetDateTime.parse(createdAt).toLocalTime().format(timeFmt)
} catch (_: Exception) {
    ""
}
