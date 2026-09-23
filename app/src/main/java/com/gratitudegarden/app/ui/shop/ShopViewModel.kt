package com.gratitudegarden.app.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gratitudegarden.app.data.GardenRepository
import com.gratitudegarden.app.data.Item
import com.gratitudegarden.app.ui.repo
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
    val owned: Set<String> = emptySet(),
    val busyItemId: String? = null,
    val message: String? = null,
)

class ShopViewModel(private val repo: GardenRepository) : ViewModel() {

    private val _ui = MutableStateFlow(ShopUiState())
    val ui: StateFlow<ShopUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { load() }
        // Reload when data changes elsewhere (coins after buying, ownership, etc.).
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
            val seeds = repo.items("seed")
            val owned = repo.inventory()
            val wallet = repo.wallet()
            val profile = repo.profile()
            _ui.update {
                it.copy(
                    loading = false,
                    seeds = seeds,
                    owned = owned,
                    coins = wallet?.balance ?: 0,
                    level = profile?.level ?: 1,
                )
            }
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, message = e.toUserMessage("Couldn't load the shop", ::friendly)) }
        }
    }

    fun buy(item: Item) {
        if (_ui.value.busyItemId != null) return
        _ui.update { it.copy(busyItemId = item.id) }
        viewModelScope.launch {
            try {
                repo.purchaseItem(item.id)
                load()
                _ui.update { it.copy(busyItemId = null, message = "Bought ${item.name} 🌱") }
            } catch (e: Exception) {
                _ui.update { it.copy(busyItemId = null, message = e.toUserMessage("Couldn't do that. Please try again.", ::friendly)) }
            }
        }
    }

    fun plant(item: Item) {
        if (_ui.value.busyItemId != null) return
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
        "level" in m && "required" in m -> "You need a higher level to grow this seed."
        else -> null
    }

    companion object {
        val Factory = viewModelFactory { initializer { ShopViewModel(repo()) } }
    }
}
