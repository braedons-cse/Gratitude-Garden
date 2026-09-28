package com.gratitudegarden.app.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gratitudegarden.app.data.GardenRepository
import com.gratitudegarden.app.data.Item
import com.gratitudegarden.app.data.streakNow
import com.gratitudegarden.app.ui.NEEDS_CONNECTION_MESSAGE
import com.gratitudegarden.app.ui.isOffline
import com.gratitudegarden.app.ui.repo
import com.gratitudegarden.app.ui.showIn
import com.gratitudegarden.app.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShopUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val coins: Int = 0,
    val level: Int = 1,
    val seeds: List<Item> = emptyList(),
    val backdrops: List<Item> = emptyList(),
    val activeBackdropId: String? = null,
    val owned: Set<String> = emptySet(),
    val busyItemId: String? = null,
    /** Streak freezes available, this month's free one included. */
    val freezes: Int = 0,
    val buyingFreeze: Boolean = false,
    val message: String? = null,
    /** Buying and planting are online-only. */
    val online: Boolean = true,
)

class ShopViewModel(private val repo: GardenRepository) : ViewModel() {

    private val _ui = MutableStateFlow(ShopUiState())
    val ui: StateFlow<ShopUiState> = _ui.asStateFlow()

    init {
        showIn(_ui, repo.observeCatalog()) { items ->
            copy(seeds = items.filter { it.category == "seed" }, backdrops = items.filter { it.category == "backdrop" })
        }
        showIn(_ui, repo.observeGarden()) { copy(activeBackdropId = it?.activeBackdropItemId) }
        showIn(_ui, repo.observeInventory()) { copy(owned = it) }
        showIn(_ui, repo.observeWallet()) { copy(coins = it?.balance ?: 0) }
        showIn(_ui, repo.observeProfile()) { copy(level = it?.level ?: 1) }
        showIn(_ui, repo.observeStats()) { copy(freezes = it?.streakNow?.freezes ?: 0) }
        showIn(_ui, repo.isOnline) { copy(online = it) }
        viewModelScope.launch { pull(force = false) }
    }

    /** User-initiated pull-to-refresh — shows the spinner while reloading. */
    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(refreshing = true) }
            pull(force = true)
            _ui.update { it.copy(refreshing = false) }
        }
    }

    /**
     * Only a refresh the user asked for ([force]) reports being offline; the one on opening
     * the screen stays quiet, since the shop already says it's offline.
     */
    private suspend fun pull(force: Boolean) {
        try {
            repo.refreshAll(force)
        } catch (e: Exception) {
            if (force || !isOffline(e)) {
                _ui.update { it.copy(message = e.toUserMessage("Couldn't load the shop", ::friendly)) }
            }
        }
        _ui.update { it.copy(loading = false) }
    }

    /** The buttons are disabled offline; this covers the moment the network drops. */
    private fun needsConnection(): Boolean {
        if (_ui.value.online) return false
        _ui.update { it.copy(message = NEEDS_CONNECTION_MESSAGE) }
        return true
    }

    fun buy(item: Item) {
        if (_ui.value.busyItemId != null || needsConnection()) return
        _ui.update { it.copy(busyItemId = item.id) }
        viewModelScope.launch {
            try {
                repo.purchaseItem(item.id)
                // Buying doesn't equip: a backdrop waits for "Use", as a seed waits for planting.
                val done = if (item.category == "backdrop") "Bought ${item.name}. Tap Use to show it." else "Bought ${item.name} 🌱"
                _ui.update { it.copy(busyItemId = null, message = done) }
            } catch (e: Exception) {
                _ui.update { it.copy(busyItemId = null, message = e.toUserMessage("Couldn't do that. Please try again.", ::friendly)) }
            }
        }
    }

    fun equip(item: Item) {
        if (_ui.value.busyItemId != null || needsConnection()) return
        _ui.update { it.copy(busyItemId = item.id) }
        viewModelScope.launch {
            try {
                repo.setActiveBackdrop(item.id)
                _ui.update { it.copy(busyItemId = null, message = "${item.name} is now your backdrop") }
            } catch (e: Exception) {
                _ui.update { it.copy(busyItemId = null, message = e.toUserMessage("Couldn't do that. Please try again.", ::friendly)) }
            }
        }
    }

    fun buyFreeze() {
        if (_ui.value.buyingFreeze || needsConnection()) return
        _ui.update { it.copy(buyingFreeze = true) }
        viewModelScope.launch {
            try {
                repo.buyStreakFreeze()
                _ui.update { it.copy(buyingFreeze = false, message = "Streak freeze ready ❄️") }
            } catch (e: Exception) {
                _ui.update { it.copy(buyingFreeze = false, message = e.toUserMessage("Couldn't do that. Please try again.", ::friendly)) }
            }
        }
    }

    fun plant(item: Item) {
        if (_ui.value.busyItemId != null || needsConnection()) return
        _ui.update { it.copy(busyItemId = item.id) }
        viewModelScope.launch {
            try {
                val placed = repo.plantInFirstEmptyCell(item.id)
                _ui.update {
                    it.copy(
                        busyItemId = null,
                        message = if (placed) "Planted ${item.name} 🌿" else "Your garden is full!",
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(busyItemId = null, message = e.toUserMessage("Couldn't do that. Please try again.", ::friendly)) }
            }
        }
    }

    fun consumeMessage() {
        if (_ui.value.message != null) _ui.update { it.copy(message = null) }
    }

    /** Server errors raised on purpose by the shop RPCs; null means "use the fallback". */
    private fun friendly(m: String): String? = when {
        "insufficient coins" in m -> "Not enough coins yet — plant more kind thoughts."
        "level" in m && "required" in m -> "You need a higher level for that."
        "streak freeze limit" in m -> "You already have all the freezes you can hold."
        "do not own this backdrop" in m -> "Buy that backdrop first."
        "no longer available" in m -> "That's no longer in the shop."
        else -> null
    }

    companion object {
        val Factory = viewModelFactory { initializer { ShopViewModel(repo()) } }
    }
}
