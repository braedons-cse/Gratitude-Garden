package com.gratitudegarden.app.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gratitudegarden.app.data.GratitudeEntry
import com.gratitudegarden.app.data.GardenRepository
import com.gratitudegarden.app.data.StreakStatus
import com.gratitudegarden.app.data.streakNow
import com.gratitudegarden.app.ui.isOffline
import com.gratitudegarden.app.ui.repo
import com.gratitudegarden.app.ui.showIn
import com.gratitudegarden.app.ui.toUserMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class JournalSection(val label: String, val entries: List<GratitudeEntry>)

data class JournalUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val endReached: Boolean = false,
    val totalEntries: Int = 0,
    val streak: Int = 0,
    /** Days were missed and freezes cover them; the next entry spends them. */
    val streakHeld: Boolean = false,
    val sections: List<JournalSection> = emptyList(),
    val entryDates: Set<String> = emptySet(),
    /** Days in the week strip that a streak freeze covered. */
    val frozenDates: Set<String> = emptySet(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModel(private val repo: GardenRepository) : ViewModel() {

    private val _ui = MutableStateFlow(JournalUiState())
    val ui: StateFlow<JournalUiState> = _ui.asStateFlow()

    // How many entries the list shows. The whole history is synced into Room, so paging
    // is only a bigger LIMIT on a local query: no network, and nothing to wait for.
    private val limit = MutableStateFlow(PAGE_SIZE)

    // Whether the first refresh has finished (or failed). Until then an empty Room most
    // likely means "not downloaded yet", not "no entries", so the empty state waits.
    private var firstRefreshDone = false

    init {
        showIn(_ui, repo.observeStats()) {
            val s = it?.streakNow ?: StreakStatus()
            copy(totalEntries = it?.totalEntries ?: 0, streak = s.days, streakHeld = s.heldByFreeze)
        }
        // One row past the limit tells whether there is anything left to show.
        val page = limit.flatMapLatest { n -> repo.observeEntries(n + 1).map { n to it } }
        showIn(_ui, page) { (n, rows) ->
            copy(
                loading = rows.isEmpty() && !firstRefreshDone,
                sections = sectionsOf(rows.take(n)),
                endReached = rows.size <= n,
            )
        }
        showIn(_ui, repo.observeRecentEntryDates()) { dates ->
            copy(entryDates = dates.map { atMostToday(it) }.toSet())
        }
        showIn(_ui, repo.observeRecentFrozenDates()) { copy(frozenDates = it) }
        viewModelScope.launch { pull(force = false) }
    }

    /** User-initiated pull-to-refresh — shows the spinner while syncing. */
    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(refreshing = true) }
            pull(force = true)
            _ui.update { it.copy(refreshing = false) }
        }
    }

    /**
     * Only a refresh the user asked for ([force]) reports being offline. The one on opening
     * the screen stays quiet: the journal is on screen from the device, and it works offline.
     */
    private suspend fun pull(force: Boolean) {
        val error = try {
            repo.refreshAll(force)
            null
        } catch (e: Exception) {
            if (force || !isOffline(e)) e.toUserMessage("Couldn't load your journal") else null
        }
        firstRefreshDone = true
        _ui.update { it.copy(loading = false, error = error) }
    }

    /** Show more of the history as the user nears the bottom of the list. Safe to call repeatedly. */
    fun loadMore() {
        if (_ui.value.endReached) return
        limit.update { it + PAGE_SIZE }
    }

    private fun sectionsOf(entries: List<GratitudeEntry>): List<JournalSection> {
        val today = LocalDate.now()
        return entries.groupBy { atMostToday(it.entryDate) }.map { (date, items) ->
            JournalSection(label = labelFor(date, today), entries = items)
        }
    }

    /**
     * The server never dates an entry before the previous one, so after a zone change (or
     * the move off UTC days) an entry can carry tomorrow's local date. It was written today;
     * show it as today. ISO dates compare correctly as strings.
     */
    private fun atMostToday(date: String): String = minOf(date, LocalDate.now().toString())

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
            try { repo.editEntry(id, newText.trim()) }
            catch (e: Exception) { _ui.update { it.copy(error = e.toUserMessage("Couldn't save your edit")) } }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try { repo.deleteEntry(id) }
            catch (e: Exception) { _ui.update { it.copy(error = e.toUserMessage("Couldn't delete that entry")) } }
        }
    }

    /** Send an entry the server refused again, as it is now. */
    fun retry(id: String) {
        viewModelScope.launch {
            try { repo.retryEntry(id) }
            catch (e: Exception) { _ui.update { it.copy(error = e.toUserMessage("Couldn't try that again")) } }
        }
    }

    /** Give up on a refused change: the server's copy comes back, or the entry goes if it never got there. */
    fun discard(id: String) {
        viewModelScope.launch {
            try { repo.discardEntry(id) }
            catch (e: Exception) { _ui.update { it.copy(error = e.toUserMessage("Couldn't discard that change")) } }
        }
    }

    /** Keep a refused entry's words as a new thought (its original was deleted elsewhere). */
    fun saveAsNew(id: String) {
        viewModelScope.launch {
            try { repo.saveAsNewEntry(id) }
            catch (e: Exception) { _ui.update { it.copy(error = e.toUserMessage("Couldn't save that as a new thought", ::friendly)) } }
        }
    }

    /** Server errors a journal action can meet on purpose; null means "use the fallback". */
    private fun friendly(m: String): String? = when {
        "daily entry cap" in m -> "That's all your thoughts for today. Come back tomorrow."
        else -> null
    }

    fun consumeError() {
        if (_ui.value.error != null) _ui.update { it.copy(error = null) }
    }

    companion object {
        private const val PAGE_SIZE = 20

        val Factory = viewModelFactory { initializer { JournalViewModel(repo()) } }
    }
}
