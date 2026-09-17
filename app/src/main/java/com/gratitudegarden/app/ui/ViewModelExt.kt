package com.gratitudegarden.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.gratitudegarden.app.GratitudeGardenApplication
import com.gratitudegarden.app.data.AdminRepository
import com.gratitudegarden.app.data.GardenRepository

/** Reach the Application (and its [GardenRepository]) from a ViewModel factory. */
fun CreationExtras.gardenApp(): GratitudeGardenApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GratitudeGardenApplication

fun CreationExtras.repo(): GardenRepository = gardenApp().container.gardenRepository

fun CreationExtras.adminRepo(): AdminRepository = gardenApp().container.adminRepository
