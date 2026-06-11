package com.cse5236.gratitudegarden.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.cse5236.gratitudegarden.model.GardenState
import com.cse5236.gratitudegarden.model.addGratitudeEntry
import com.cse5236.gratitudegarden.model.getPlantStage
import com.cse5236.gratitudegarden.model.waterPlant
import com.cse5236.gratitudegarden.util.LogTags

enum class CurrentScreen {
    GARDEN,
    GRATITUDE_ENTRY
}

interface ClickHandler {
    fun onClick()
}

interface SubmitEntryHandler {
    fun onSubmit(entryText: String)
}

@Composable
fun GratitudeGardenApp() {
    var currentScreen by remember { mutableStateOf(CurrentScreen.GARDEN) }
    var gardenState by remember { mutableStateOf(GardenState()) }

    val waterPlantHandler = object : ClickHandler {
        override fun onClick() {
            val oldCoins = gardenState.coins
            val oldGrowth = gardenState.plantGrowth

            gardenState = waterPlant(gardenState)

            Log.d(
                LogTags.APP_LOGIC,
                "Water plant clicked. Old coins: $oldCoins, New coins: ${gardenState.coins}, Old growth: $oldGrowth, New growth: ${gardenState.plantGrowth}"
            )
        }
    }

    val addEntryHandler = object : ClickHandler {
        override fun onClick() {
            Log.d(LogTags.APP_LOGIC, "Navigating from Garden Screen to Gratitude Entry Screen")
            currentScreen = CurrentScreen.GRATITUDE_ENTRY
        }
    }

    val submitEntryHandler = object : SubmitEntryHandler {
        override fun onSubmit(entryText: String) {
            val oldCoins = gardenState.coins

            gardenState = addGratitudeEntry(gardenState, entryText)

            Log.d(
                LogTags.APP_LOGIC,
                "Gratitude entry submitted. Entry: $entryText, Old coins: $oldCoins, New coins: ${gardenState.coins}"
            )

            currentScreen = CurrentScreen.GARDEN
        }
    }

    val backHandler = object : ClickHandler {
        override fun onClick() {
            Log.d(LogTags.APP_LOGIC, "Navigating from Gratitude Entry Screen back to Garden Screen")
            currentScreen = CurrentScreen.GARDEN
        }
    }

    if (currentScreen == CurrentScreen.GARDEN) {
        GardenScreen(
            gardenState = gardenState,
            waterPlantHandler = waterPlantHandler,
            addEntryHandler = addEntryHandler
        )
    } else {
        GratitudeEntryScreen(
            submitEntryHandler = submitEntryHandler,
            backHandler = backHandler
        )
    }
}

@Composable
fun GardenScreen(
    gardenState: GardenState,
    waterPlantHandler: ClickHandler,
    addEntryHandler: ClickHandler
) {
    LogComposableLifecycle(LogTags.GARDEN_SCREEN)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Gratitude Garden",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(text = "Coins: ${gardenState.coins}")
                Text(text = "Plant Stage: ${getPlantStage(gardenState.plantGrowth)}")
                Text(text = "Plant Growth Level: ${gardenState.plantGrowth}")

                if (gardenState.lastEntry.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Last Gratitude Entry:")
                    Text(text = gardenState.lastEntry)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                waterPlantHandler.onClick()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Water Plant - Costs 3 Coins")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                addEntryHandler.onClick()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Add Gratitude Entry")
        }
    }
}

@Composable
fun GratitudeEntryScreen(
    submitEntryHandler: SubmitEntryHandler,
    backHandler: ClickHandler
) {
    LogComposableLifecycle(LogTags.ENTRY_SCREEN)

    var entryText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Add Gratitude Entry",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = entryText,
            onValueChange = {
                entryText = it
                Log.d(LogTags.APP_LOGIC, "Entry text changed: $entryText")
            },
            label = {
                Text(text = "What are you grateful for?")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                submitEntryHandler.onSubmit(entryText)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Submit Entry and Earn 5 Coins")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                backHandler.onClick()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Back to Garden")
        }
    }
}

@Composable
fun LogComposableLifecycle(tag: String) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        Log.d(tag, "Composable entered screen. This is similar to onCreateView.")
    }

    DisposableEffect(lifecycleOwner) {
        Log.d(tag, "DisposableEffect started. Screen is active.")

        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_CREATE) {
                    Log.d(tag, "onCreate called")
                } else if (event == Lifecycle.Event.ON_START) {
                    Log.d(tag, "onStart called")
                } else if (event == Lifecycle.Event.ON_RESUME) {
                    Log.d(tag, "onResume called")
                } else if (event == Lifecycle.Event.ON_PAUSE) {
                    Log.d(tag, "onPause called")
                } else if (event == Lifecycle.Event.ON_STOP) {
                    Log.d(tag, "onStop called")
                } else if (event == Lifecycle.Event.ON_DESTROY) {
                    Log.d(tag, "onDestroy called")
                } else {
                    Log.d(tag, "Other lifecycle event: $event")
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            Log.d(tag, "Composable left screen. This is similar to onDestroyView.")
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}