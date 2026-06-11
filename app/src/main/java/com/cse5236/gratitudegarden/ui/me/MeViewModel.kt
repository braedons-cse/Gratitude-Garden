package com.cse5236.gratitudegarden.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cse5236.gratitudegarden.data.GardenRepository
import com.cse5236.gratitudegarden.ui.repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeUiState(
    val loading: Boolean = true,
    val name: String = "",
    val level: Int = 1,
    val coins: Int = 0,
    val streak: Int = 0,
    val totalEntries: Int = 0,
)

class MeViewModel(private val repo: GardenRepository) : ViewModel() {

    private val _ui = MutableStateFlow(MeUiState())
    val ui: StateFlow<MeUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val profile = repo.profile()
                val wallet = repo.wallet()
                val stats = repo.stats()
                _ui.update {
                    it.copy(
                        loading = false,
                        name = profile?.displayName ?: "Gardener",
                        level = profile?.level ?: 1,
                        coins = wallet?.balance ?: 0,
                        streak = stats?.currentStreak ?: 0,
                        totalEntries = stats?.totalEntries ?: 0,
                    )
                }
            } catch (_: Exception) {
                _ui.update { it.copy(loading = false) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { MeViewModel(repo()) } }
    }
}
