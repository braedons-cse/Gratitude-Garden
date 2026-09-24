package com.gratitudegarden.app.ui.garden

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gratitudegarden.app.data.GardenPlantRow
import com.gratitudegarden.app.data.GardenRepository
import com.gratitudegarden.app.data.Item
import com.gratitudegarden.app.data.SubmitResult
import com.gratitudegarden.app.data.effectiveStreak
import com.gratitudegarden.app.notifications.ReminderPreferences
import com.gratitudegarden.app.notifications.ReminderScheduler
import com.gratitudegarden.app.ui.gardenApp
import com.gratitudegarden.app.ui.repo
import com.gratitudegarden.app.ui.showIn
import com.gratitudegarden.app.ui.toUserMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GardenUiState(
    /** True until Room's first read; the screen stays blank rather than show defaults. */
    val loading: Boolean = true,
    val displayName: String = "",
    val gardenName: String = "My Garden",
    val coins: Int = 0,
    val streak: Int = 0,
    val plants: List<GardenPlantRow> = emptyList(),
    val gridRows: Int = 6,
    val gridCols: Int = 5,
    val itemSlugs: Map<String, String> = emptyMap(),
    val ownedSeeds: List<Item> = emptyList(),
    val dailyCap: Int = 10,
    val usedToday: Int = 0,
    val submitting: Boolean = false,
    val watering: Boolean = false,
    val placing: Boolean = false,
    val message: String? = null,
    val showNotifPrompt: Boolean = false,
) {
    val thoughtsLeft: Int get() = (dailyCap - usedToday).coerceAtLeast(0)
}

class GardenViewModel(
    private val repo: GardenRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _ui = MutableStateFlow(GardenUiState())
    val ui: StateFlow<GardenUiState> = _ui.asStateFlow()

    init {
        showIn(_ui, repo.observeProfile()) { copy(displayName = it?.displayName ?: "") }
        showIn(_ui, repo.observeWallet()) { copy(coins = it?.balance ?: 0) }
        showIn(_ui, repo.observeStats()) { copy(streak = it?.effectiveStreak ?: 0) }
        showIn(_ui, repo.observeGarden()) {
            copy(
                loading = false,
                gardenName = it?.name ?: "My Garden",
                gridRows = it?.gridRows ?: 6,
                gridCols = it?.gridCols ?: 5,
            )
        }
        showIn(_ui, repo.observePlants()) { copy(plants = it) }
        showIn(_ui, repo.observeCatalog()) { copy(itemSlugs = it.associate { item -> item.id to item.slug }) }
        showIn(_ui, ownedSeeds()) { copy(ownedSeeds = it) }
        showIn(_ui, repo.observeEntriesTodayCount()) { copy(usedToday = it) }
        showIn(_ui, repo.observeDailyCap()) { copy(dailyCap = it) }
        refresh()
    }

    private fun ownedSeeds(): Flow<List<Item>> =
        combine(repo.observeCatalog(), repo.observeInventory()) { items, owned ->
            items.filter { it.category == "seed" && it.id in owned }
        }

    /**
     * Pull fresh data into Room. The screen already shows the last good copy, so a failure
     * only costs a message, never the garden.
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                repo.refreshAll()
            } catch (e: Exception) {
                _ui.update { it.copy(message = e.toUserMessage("Couldn't load your garden", ::friendly)) }
            }
        }
    }

    fun submit(text: String, voice: Boolean) {
        if (text.isBlank() || _ui.value.submitting) return
        _ui.update { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                val message = when (val result = repo.submitEntry(text.trim(), voice)) {
                    // The server decides the reward, so the toast reports what was
                    // actually awarded rather than promising a fixed number.
                    is SubmitResult.Planted -> {
                        val note = if (result.coins != null) "+${result.coins} coins · " else ""
                        "${note}a kind thought planted 🌱"
                    }
                    SubmitResult.Saved -> "Saved in your journal. It'll be planted as soon as it syncs."
                    is SubmitResult.Refused -> friendly(result.reason) ?: "The garden couldn't take that thought."
                }
                _ui.update { it.copy(submitting = false, message = message) }
                maybeOfferReminders()
            } catch (e: Exception) {
                _ui.update { it.copy(submitting = false, message = e.toUserMessage("Couldn't save your thought", ::friendly)) }
            }
        }
    }

    /**
     * After a gratitude entry saves, show the one-time "enable daily reminders?"
     * prompt if this user has never seen it. The seen-flag lives in Supabase
     * (per-user, mirrored in Room), so it's shown exactly once per account — not
     * once per install.
     */
    private suspend fun maybeOfferReminders() {
        val alreadyAsked = runCatching { repo.notifPromptSeen() }.getOrDefault(true)
        if (!alreadyAsked) _ui.update { it.copy(showNotifPrompt = true) }
    }

    /**
     * Resolve the one-time prompt. [enable] true means the user opted in — turn
     * reminders on locally and schedule the daily job (the caller is responsible
     * for having requested the POST_NOTIFICATIONS grant first on Android 13+).
     * Either way the prompt is marked seen so it never reappears.
     */
    fun onReminderPromptDecided(enable: Boolean) {
        _ui.update { it.copy(showNotifPrompt = false) }
        viewModelScope.launch {
            runCatching { repo.markNotifPromptSeen() }
            if (enable) {
                val prefs = ReminderPreferences(appContext)
                prefs.setEnabled(true)
                val s = prefs.current()
                ReminderScheduler.schedule(appContext, s.hour, s.minute)
            }
        }
    }

    fun water(plantId: String) {
        if (_ui.value.watering) return
        _ui.update { it.copy(watering = true) }
        viewModelScope.launch {
            try {
                repo.waterPlant(plantId)
                _ui.update { it.copy(watering = false, message = "Watered 🌱 · -10 coins") }
            } catch (e: Exception) {
                _ui.update { it.copy(watering = false, message = e.toUserMessage("Couldn't water your plant", ::friendly)) }
            }
        }
    }

    /** Plant an owned seed into a specific cell (chosen by tapping soil or from the Shop). */
    fun plantSeedAt(itemId: String, x: Int, y: Int) {
        if (_ui.value.placing) return
        _ui.update { it.copy(placing = true) }
        viewModelScope.launch {
            try {
                repo.placePlant(itemId, x, y)
                _ui.update { it.copy(placing = false, message = "Planted 🌿") }
            } catch (e: Exception) {
                _ui.update { it.copy(placing = false, message = e.toUserMessage("Couldn't do that. Please try again.", ::friendly)) }
            }
        }
    }

    /** Move an existing plant to a new cell (drag-and-drop). */
    fun movePlant(plantId: String, x: Int, y: Int) {
        if (_ui.value.placing) return
        _ui.update { it.copy(placing = true) }
        viewModelScope.launch {
            try {
                repo.movePlant(plantId, x, y)
                _ui.update { it.copy(placing = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(placing = false, message = e.toUserMessage("Couldn't do that. Please try again.", ::friendly)) }
            }
        }
    }

    /** Dig up (remove) a plant from the garden. */
    fun digUp(plantId: String) {
        if (_ui.value.placing) return
        _ui.update { it.copy(placing = true) }
        viewModelScope.launch {
            try {
                repo.digUpPlant(plantId)
                _ui.update { it.copy(placing = false, message = "Dug up 🪴") }
            } catch (e: Exception) {
                _ui.update { it.copy(placing = false, message = e.toUserMessage("Couldn't do that. Please try again.", ::friendly)) }
            }
        }
    }

    /** Server errors raised on purpose by the garden RPCs; null means "use the fallback". */
    private fun friendly(m: String): String? = when {
        "occupied" in m -> "That spot's already taken — try another."
        "out of bounds" in m -> "That's outside the garden."
        "do not own" in m -> "You don't own that seed yet."
        "daily entry cap" in m -> "That's all your thoughts for today. Come back tomorrow."
        "insufficient coins" in m -> "Not enough coins yet — plant more kind thoughts."
        "entry text required" in m -> "Write a few words first."
        else -> null
    }

    fun consumeMessage() {
        if (_ui.value.message != null) _ui.update { it.copy(message = null) }
    }

    companion object {
        val Factory = viewModelFactory { initializer { GardenViewModel(repo(), gardenApp()) } }
    }
}
