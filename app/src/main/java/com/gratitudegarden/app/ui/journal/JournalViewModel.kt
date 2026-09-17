package com.gratitudegarden.app.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gratitudegarden.app.data.GratitudeEntry
import com.gratitudegarden.app.data.GardenRepository
import com.gratitudegarden.app.data.effectiveStreak
import com.gratitudegarden.app.ui.repo
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
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val totalEntries: Int = 0,
    val streak: Int = 0,
    val sections: List<JournalSection> = emptyList(),
    val entryDates: Set<String> = emptySet(),
    val error: String? = null,
)

class JournalViewModel(private val repo: GardenRepository) : ViewModel() {

    private val _ui = MutableStateFlow(JournalUiState())
    val ui: StateFlow<JournalUiState> = _ui.asStateFlow()

    // Accumulated entries across the pages loaded so far (newest first).
    private var loaded: List<GratitudeEntry> = emptyList()
    private var pageInFlight = false

    init {
        viewModelScope.launch { loadFirstPage() }
        // Reload the first page when entries change (e.g. a new thought planted from
        // the Garden). This resets pagination to the top — the newest entries.
        viewModelScope.launch { repo.changes.collect { loadFirstPage() } }
    }

    /** User-initiated pull-to-refresh — shows the spinner while reloading page one. */
    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(refreshing = true) }
            loadFirstPage()
            _ui.update { it.copy(refreshing = false) }
        }
    }

    private suspend fun loadFirstPage() {
        try {
            val stats = repo.stats()
            val page = repo.entriesPage(limit = PAGE_SIZE)
            val recentDates = repo.recentEntryDates()
            loaded = page
            _ui.update {
                it.copy(
                    loading = false,
                    totalEntries = stats?.totalEntries ?: page.size,
                    streak = stats?.effectiveStreak ?: 0,
                    sections = sectionsOf(loaded),
                    entryDates = recentDates,
                    endReached = page.size < PAGE_SIZE,
                    error = null,
                )
            }
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, error = e.message ?: "Couldn't load your journal") }
        }
    }

    /**
     * Fetch the next page when the user nears the bottom of the list. Safe to call
     * repeatedly — it no-ops while a page is in flight or once the end is reached.
     */
    fun loadMore() {
        if (pageInFlight) return
        val s = _ui.value
        if (s.loading || s.endReached) return
        pageInFlight = true
        viewModelScope.launch {
            _ui.update { it.copy(loadingMore = true) }
            try {
                val before = loaded.lastOrNull()?.createdAt
                val next = repo.entriesPage(limit = PAGE_SIZE, createdBefore = before)
                loaded = loaded + next
                _ui.update {
                    it.copy(
                        sections = sectionsOf(loaded),
                        endReached = next.size < PAGE_SIZE,
                        loadingMore = false,
                    )
                }
            } catch (_: Exception) {
                _ui.update { it.copy(loadingMore = false) }
            } finally {
                pageInFlight = false
            }
        }
    }

    private fun sectionsOf(entries: List<GratitudeEntry>): List<JournalSection> {
        val today = LocalDate.now()
        return entries.groupBy { it.entryDate }.map { (date, items) ->
            JournalSection(label = labelFor(date, today), entries = items)
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
            try { repo.editEntry(id, newText.trim()); loadFirstPage() }
            catch (e: Exception) { _ui.update { it.copy(error = e.message ?: "Couldn't edit") } }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try { repo.deleteEntry(id); loadFirstPage() }
            catch (e: Exception) { _ui.update { it.copy(error = e.message ?: "Couldn't delete") } }
        }
    }

    companion object {
        private const val PAGE_SIZE = 20

        val Factory = viewModelFactory { initializer { JournalViewModel(repo()) } }
    }
}
