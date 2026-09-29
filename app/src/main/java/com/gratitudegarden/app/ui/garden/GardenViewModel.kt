package com.gratitudegarden.app.ui.garden

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gratitudegarden.app.data.AppSession
import com.gratitudegarden.app.data.GardenPlantRow
import com.gratitudegarden.app.data.GardenRepository
import com.gratitudegarden.app.data.Item
import com.gratitudegarden.app.data.LevelPreferences
import com.gratitudegarden.app.data.StreakStatus
import com.gratitudegarden.app.data.streakNow
import com.gratitudegarden.app.notifications.ReminderPreferences
import com.gratitudegarden.app.notifications.ReminderScheduler
import com.gratitudegarden.app.ui.NEEDS_CONNECTION_MESSAGE
import com.gratitudegarden.app.ui.gardenApp
import com.gratitudegarden.app.ui.isOffline
import com.gratitudegarden.app.ui.repo
import com.gratitudegarden.app.ui.showIn
import com.gratitudegarden.app.ui.toUserMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A level reached and not yet congratulated on this device, and the seeds and backdrops it opened up. */
data class LevelUp(val level: Int, val unlocked: List<String>)

data class GardenUiState(
    /** True until Room's first read; the screen stays blank rather than show defaults. */
    val loading: Boolean = true,
    val displayName: String = "",
    val gardenName: String = "My Garden",
    val coins: Int = 0,
    val streak: Int = 0,
    /** Days were missed and freezes cover them; the next entry spends them. */
    val streakHeld: Boolean = false,
    val plants: List<GardenPlantRow> = emptyList(),
    val gridRows: Int = 6,
    val gridCols: Int = 5,
    /** Drawn above the plot; its slug comes from [itemSlugs]. */
    val activeBackdropId: String? = null,
    val itemSlugs: Map<String, String> = emptyMap(),
    val ownedSeeds: List<Item> = emptyList(),
    val dailyCap: Int = 10,
    val usedToday: Int = 0,
    val submitting: Boolean = false,
    val watering: Boolean = false,
    val placing: Boolean = false,
    val message: String? = null,
    val showNotifPrompt: Boolean = false,
    val levelUp: LevelUp? = null,
    /** Writing works offline; the economy (plant, water, move, dig) doesn't. */
    val online: Boolean = true,
) {
    val thoughtsLeft: Int get() = (dailyCap - usedToday).coerceAtLeast(0)
}

class GardenViewModel(
    private val repo: GardenRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _ui = MutableStateFlow(GardenUiState())
    val ui: StateFlow<GardenUiState> = _ui.asStateFlow()

    private val levelPrefs = LevelPreferences(appContext)

    // Captured once: this ViewModel lives inside the signed-in user's SessionScope.
    private val userId = (repo.session.value as? AppSession.SignedIn)?.userId

    init {
        showIn(_ui, repo.observeProfile()) { copy(displayName = it?.displayName ?: "") }
        showIn(_ui, repo.observeWallet()) { copy(coins = it?.balance ?: 0) }
        showIn(_ui, repo.observeStats()) {
            val s = it?.streakNow ?: StreakStatus()
            copy(streak = s.days, streakHeld = s.heldByFreeze)
        }
        showIn(_ui, repo.observeGarden()) {
            copy(
                loading = false,
                gardenName = it?.name ?: "My Garden",
                gridRows = it?.gridRows ?: 6,
                gridCols = it?.gridCols ?: 5,
                activeBackdropId = it?.activeBackdropItemId,
            )
        }
        showIn(_ui, repo.observePlants()) { copy(plants = it) }
        showIn(_ui, repo.observeCatalog()) { copy(itemSlugs = it.associate { item -> item.id to item.slug }) }
        showIn(_ui, ownedSeeds()) { copy(ownedSeeds = it) }
        showIn(_ui, repo.observeEntriesTodayCount()) { copy(usedToday = it) }
        showIn(_ui, repo.observeDailyCap()) { copy(dailyCap = it) }
        showIn(_ui, repo.isOnline) { copy(online = it) }
        watchLevel()
        refresh()
    }

    /**
     * Offer the level-up dialog whenever the synced level passes the last one celebrated
     * here. Keyed off the profile rather than the submit, so an entry the outbox delivered
     * in the background still gets its moment the next time the Garden opens. The first
     * level seen on a device is only recorded, so signing in isn't greeted with a level-up.
     */
    private fun watchLevel() {
        val uid = userId ?: return
        viewModelScope.launch {
            combine(
                repo.observeProfile().filterNotNull(),
                levelPrefs.celebrated(uid),
                repo.observeCatalog(),
            ) { profile, celebrated, items -> Triple(profile.level, celebrated, items) }
                .collect { (level, celebrated, items) ->
                    when {
                        // First sighting, or an admin lowered the level: just remember it.
                        celebrated == null || level < celebrated -> levelPrefs.setCelebrated(uid, level)
                        level > celebrated -> {
                            val unlocked = items.filter {
                                it.category in UNLOCKABLE && it.isPurchasable &&
                                    it.levelRequired in (celebrated + 1)..level
                            }.map { it.name }
                            _ui.update { it.copy(levelUp = LevelUp(level, unlocked)) }
                        }
                        else -> _ui.update { it.copy(levelUp = null) }
                    }
                }
        }
    }

    /** The level-up dialog was closed; don't show this level again. */
    fun onLevelUpSeen() {
        val uid = userId ?: return
        val level = _ui.value.levelUp?.level ?: return
        _ui.update { it.copy(levelUp = null) }
        viewModelScope.launch { levelPrefs.setCelebrated(uid, level) }
    }

    private fun ownedSeeds(): Flow<List<Item>> =
        combine(repo.observeCatalog(), repo.observeInventory()) { items, owned ->
            items.filter { it.category == "seed" && it.id in owned }
        }

    /**
     * Pull fresh data into Room. The screen already shows the last good copy, so a failure
     * only costs a message, never the garden. Offline it costs nothing at all: the banner
     * already says so, and the refresh runs by itself when the connection returns.
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                repo.refreshAll()
            } catch (e: Exception) {
                if (!isOffline(e)) {
                    _ui.update { it.copy(message = e.toUserMessage("Couldn't load your garden", ::gardenErrorMessage)) }
                }
            }
        }
    }

    fun submit(text: String, voice: Boolean) {
        if (text.isBlank() || _ui.value.submitting) return
        // A held streak is one the server bridges with freezes on this entry, the day's first.
        val freezing = _ui.value.streakHeld
        _ui.update { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                val message = submitMessage(repo.submitEntry(text.trim(), voice), freezing)
                _ui.update { it.copy(submitting = false, message = message) }
                maybeOfferReminders()
            } catch (e: Exception) {
                _ui.update { it.copy(submitting = false, message = e.toSubmitMessage()) }
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

    /**
     * The economy is online-only: say so up front rather than let the request fail. The
     * screen disables what it can; this covers the rest (and the moment the network drops).
     */
    private fun needsConnection(): Boolean {
        if (_ui.value.online) return false
        _ui.update { it.copy(message = NEEDS_CONNECTION_MESSAGE) }
        return true
    }

    /** A garden action the screen caught offline before it reached the ViewModel. */
    fun onNeedsConnection() {
        needsConnection()
    }

    fun water(plantId: String) {
        if (_ui.value.watering || needsConnection()) return
        _ui.update { it.copy(watering = true) }
        viewModelScope.launch {
            try {
                repo.waterPlant(plantId)
                _ui.update { it.copy(watering = false, message = "Watered 🌱 · -10 coins") }
            } catch (e: Exception) {
                _ui.update { it.copy(watering = false, message = e.toUserMessage("Couldn't water your plant", ::gardenErrorMessage)) }
            }
        }
    }

    /** Plant an owned seed into a specific cell (chosen by tapping soil or from the Shop). */
    fun plantSeedAt(itemId: String, x: Int, y: Int) {
        if (_ui.value.placing || needsConnection()) return
        _ui.update { it.copy(placing = true) }
        viewModelScope.launch {
            try {
                repo.placePlant(itemId, x, y)
                _ui.update { it.copy(placing = false, message = "Planted 🌿") }
            } catch (e: Exception) {
                _ui.update { it.copy(placing = false, message = e.toUserMessage("Couldn't do that. Please try again.", ::gardenErrorMessage)) }
            }
        }
    }

    /** Move an existing plant to a new cell (drag-and-drop). */
    fun movePlant(plantId: String, x: Int, y: Int) {
        if (_ui.value.placing || needsConnection()) return
        _ui.update { it.copy(placing = true) }
        viewModelScope.launch {
            try {
                repo.movePlant(plantId, x, y)
                _ui.update { it.copy(placing = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(placing = false, message = e.toUserMessage("Couldn't do that. Please try again.", ::gardenErrorMessage)) }
            }
        }
    }

    /** Dig up (remove) a plant from the garden. */
    fun digUp(plantId: String) {
        if (_ui.value.placing || needsConnection()) return
        _ui.update { it.copy(placing = true) }
        viewModelScope.launch {
            try {
                repo.digUpPlant(plantId)
                _ui.update { it.copy(placing = false, message = "Dug up 🪴") }
            } catch (e: Exception) {
                _ui.update { it.copy(placing = false, message = e.toUserMessage("Couldn't do that. Please try again.", ::gardenErrorMessage)) }
            }
        }
    }

    fun consumeMessage() {
        if (_ui.value.message != null) _ui.update { it.copy(message = null) }
    }

    companion object {
        /** What a level-up announces as newly in the shop. */
        private val UNLOCKABLE = setOf("seed", "backdrop")

        val Factory = viewModelFactory { initializer { GardenViewModel(repo(), gardenApp()) } }
    }
}
