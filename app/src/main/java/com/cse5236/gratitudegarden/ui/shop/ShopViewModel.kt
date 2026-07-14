package com.cse5236.gratitudegarden.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cse5236.gratitudegarden.data.GardenRepository
import com.cse5236.gratitudegarden.data.Item
import com.cse5236.gratitudegarden.ui.repo
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
            _ui.update { it.copy(loading = false, message = friendly(e)) }
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
                _ui.update { it.copy(busyItemId = null, message = friendly(e)) }
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
                _ui.update { it.copy(busyItemId = null, message = friendly(e)) }
            }
        }
    }

    fun consumeMessage() {
        if (_ui.value.message != null) _ui.update { it.copy(message = null) }
    }

    private fun friendly(e: Exception): String {
        val m = e.message ?: return "Something went wrong"
        return when {
            "insufficient coins" in m -> "Not enough coins yet — plant more kind thoughts."
            "level" in m && "required" in m -> "You need a higher level to grow this seed."
            else -> m.take(140)
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { ShopViewModel(repo()) } }
    }
}
