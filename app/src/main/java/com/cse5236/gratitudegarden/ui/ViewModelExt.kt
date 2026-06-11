package com.cse5236.gratitudegarden.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.cse5236.gratitudegarden.GratitudeGardenApplication
import com.cse5236.gratitudegarden.data.GardenRepository

/** Reach the Application (and its [GardenRepository]) from a ViewModel factory. */
fun CreationExtras.gardenApp(): GratitudeGardenApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GratitudeGardenApplication

fun CreationExtras.repo(): GardenRepository = gardenApp().container.gardenRepository
