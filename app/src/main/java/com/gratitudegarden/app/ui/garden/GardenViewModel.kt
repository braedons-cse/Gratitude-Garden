package com.gratitudegarden.app.ui.garden

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gratitudegarden.app.data.GardenPlantRow
import com.gratitudegarden.app.data.GardenRepository
import com.gratitudegarden.app.data.Item
import com.gratitudegarden.app.data.effectiveStreak
import com.gratitudegarden.app.notifications.ReminderPreferences
import com.gratitudegarden.app.notifications.ReminderScheduler
import com.gratitudegarden.app.ui.gardenApp
import com.gratitudegarden.app.ui.repo
import com.gratitudegarden.app.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GardenUiState(
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
        refresh()
        // Reload whenever any screen mutates data (e.g. planting from the Shop).
        viewModelScope.launch { repo.changes.collect { load() } }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val profile = repo.profile()
            val wallet = repo.wallet()
            val stats = repo.stats()
            val garden = repo.garden()
            val plants = repo.plants()
            val slugs = repo.itemSlugs()
            val owned = repo.ownedSeeds()
            // Counted server-side over today's rows only. This used to filter a
            // full repo.entries() fetch — the user's entire history — client-side,
            // which both wasted the round trip and dropped soft-deleted rows. Those
            // still count against the daily cap, because deleting an entry doesn't
            // refund its coins.
            val used = repo.entriesTodayCount(stats?.lastEntryDate)
            _ui.update {
                it.copy(
                    loading = false,
                    displayName = profile?.displayName ?: "",
                    gardenName = garden?.name ?: "My Garden",
                    coins = wallet?.balance ?: 0,
                    streak = stats?.effectiveStreak ?: 0,
                    plants = plants,
                    gridRows = garden?.gridRows ?: 6,
                    gridCols = garden?.gridCols ?: 5,
                    itemSlugs = slugs,
                    ownedSeeds = owned,
                    usedToday = used,
                )
            }
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, message = e.toUserMessage("Couldn't load your garden", ::friendly)) }
        }
    }

    fun submit(text: String, voice: Boolean) {
        if (text.isBlank() || _ui.value.submitting) return
        _ui.update { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                val awarded = repo.submitEntry(text.trim(), voice)
                load()
                // The server decides the reward, so the toast reports what was
                // actually awarded rather than promising a fixed number.
                val note = if (awarded != null) "+$awarded coins · " else ""
                _ui.update { it.copy(submitting = false, message = "${note}a kind thought planted 🌱") }
                maybeOfferReminders()
            } catch (e: Exception) {
                _ui.update { it.copy(submitting = false, message = e.toUserMessage("Couldn't save your thought", ::friendly)) }
            }
        }
    }

    /**
     * After a gratitude entry saves, show the one-time "enable daily reminders?"
     * prompt if this user has never seen it. The seen-flag lives in Supabase
     * (per-user), so it's shown exactly once per account — not once per install.
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
                load()
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
                load()
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
                load()
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
                load()
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
