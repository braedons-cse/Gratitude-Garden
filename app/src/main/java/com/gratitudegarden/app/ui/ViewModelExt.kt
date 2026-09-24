package com.gratitudegarden.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.gratitudegarden.app.GratitudeGardenApplication
import com.gratitudegarden.app.data.GardenRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Reach the Application (and its [GardenRepository]) from a ViewModel factory. */
fun CreationExtras.gardenApp(): GratitudeGardenApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GratitudeGardenApplication

fun CreationExtras.repo(): GardenRepository = gardenApp().container.gardenRepository

/**
 * For as long as the ViewModel lives, fold each value of [flow] into [state] with [apply].
 * Screens get their data from Room this way, so they update whenever the tables change,
 * whichever screen or refresh changed them.
 */
fun <S, T> ViewModel.showIn(state: MutableStateFlow<S>, flow: Flow<T>, apply: S.(T) -> S) {
    viewModelScope.launch { flow.collect { value -> state.update { it.apply(value) } } }
}
