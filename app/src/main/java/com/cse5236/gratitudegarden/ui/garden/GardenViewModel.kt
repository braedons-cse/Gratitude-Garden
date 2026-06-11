package com.cse5236.gratitudegarden.ui.garden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cse5236.gratitudegarden.data.GardenPlantRow
import com.cse5236.gratitudegarden.data.GardenRepository
import com.cse5236.gratitudegarden.ui.repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    val dailyCap: Int = 10,
    val usedToday: Int = 0,
    val submitting: Boolean = false,
    val watering: Boolean = false,
    val message: String? = null,
) {
    val thoughtsLeft: Int get() = (dailyCap - usedToday).coerceAtLeast(0)
}

class GardenViewModel(private val repo: GardenRepository) : ViewModel() {

    private val _ui = MutableStateFlow(GardenUiState())
    val ui: StateFlow<GardenUiState> = _ui.asStateFlow()

    init { refresh() }

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
            val entries = repo.entries()
            val slugs = repo.itemSlugs()
            val today = LocalDate.now().toString()
            val used = entries.count { it.entryDate == today }
            _ui.update {
                it.copy(
                    loading = false,
                    displayName = profile?.displayName ?: "",
                    gardenName = garden?.name ?: "My Garden",
                    coins = wallet?.balance ?: 0,
                    streak = stats?.currentStreak ?: 0,
                    plants = plants,
                    gridRows = garden?.gridRows ?: 6,
                    gridCols = garden?.gridCols ?: 5,
                    itemSlugs = slugs,
                    usedToday = used,
                )
            }
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, message = e.message ?: "Couldn't load your garden") }
        }
    }

    fun submit(text: String, voice: Boolean) {
        if (text.isBlank() || _ui.value.submitting) return
        _ui.update { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                repo.submitEntry(text.trim(), voice)
                load()
                _ui.update { it.copy(submitting = false, message = "+5 coins · a kind thought planted 🌱") }
            } catch (e: Exception) {
                _ui.update { it.copy(submitting = false, message = e.message ?: "Couldn't save your thought") }
            }
        }
    }

    fun water(plantId: String) {
        if (_ui.value.watering) return
        _ui.update { it.copy(watering = true) }
        viewModelScope.launch {
            try {
                repo.waterPlant(plantId, 10)
                load()
                _ui.update { it.copy(watering = false, message = "Watered 🌱 · -10 coins") }
            } catch (e: Exception) {
                _ui.update { it.copy(watering = false, message = e.message ?: "Couldn't water") }
            }
        }
    }

    fun consumeMessage() {
        if (_ui.value.message != null) _ui.update { it.copy(message = null) }
    }

    companion object {
        val Factory = viewModelFactory { initializer { GardenViewModel(repo()) } }
    }
}
