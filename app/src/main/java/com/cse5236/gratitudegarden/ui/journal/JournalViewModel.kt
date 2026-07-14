package com.cse5236.gratitudegarden.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cse5236.gratitudegarden.data.GratitudeEntry
import com.cse5236.gratitudegarden.data.GardenRepository
import com.cse5236.gratitudegarden.ui.repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class JournalSection(val label: String, val entries: List<GratitudeEntry>)

data class JournalUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val totalEntries: Int = 0,
    val streak: Int = 0,
    val sections: List<JournalSection> = emptyList(),
    val entryDates: Set<String> = emptySet(),
    val error: String? = null,
)

class JournalViewModel(private val repo: GardenRepository) : ViewModel() {

    private val _ui = MutableStateFlow(JournalUiState())
    val ui: StateFlow<JournalUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { load() }
        // Reload when entries change (e.g. a new thought planted from the Garden).
        viewModelScope.launch { repo.changes.collect { load() } }
    }

    /** User-initiated pull-to-refresh — shows the spinner while reloading. */
    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(refreshing = true) }
            load()
            _ui.update { it.copy(refreshing = false) }
        }
    }

    private suspend fun load() {
        try {
            val entries = repo.entries()
            val stats = repo.stats()
            val today = LocalDate.now()
            val grouped = entries.groupBy { it.entryDate }
            val sections = grouped.entries.map { (date, items) ->
                JournalSection(label = labelFor(date, today), entries = items)
            }
            _ui.update {
                it.copy(
                    loading = false,
                    totalEntries = stats?.totalEntries ?: entries.size,
                    streak = stats?.currentStreak ?: 0,
                    sections = sections,
                    entryDates = grouped.keys.toSet(),
                    error = null,
                )
            }
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, error = e.message ?: "Couldn't load your journal") }
        }
    }

    private fun labelFor(date: String, today: LocalDate): String = try {
        when (LocalDate.parse(date)) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> LocalDate.parse(date).let { "${it.month.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3)} ${it.dayOfMonth}" }
        }
    } catch (_: Exception) {
        date
    }

    fun edit(id: String, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch {
            try { repo.editEntry(id, newText.trim()); load() }
            catch (e: Exception) { _ui.update { it.copy(error = e.message ?: "Couldn't edit") } }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try { repo.deleteEntry(id); load() }
            catch (e: Exception) { _ui.update { it.copy(error = e.message ?: "Couldn't delete") } }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { JournalViewModel(repo()) } }
    }
}
