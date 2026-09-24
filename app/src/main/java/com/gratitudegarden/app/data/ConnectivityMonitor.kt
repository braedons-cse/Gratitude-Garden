package com.gratitudegarden.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Whether the device can currently reach the internet, as the system judges it.
 *
 * "Online" means the default network has internet *and* Android has validated it, so a
 * captive portal or a Wi-Fi with no uplink counts as offline. Watches for the life of the
 * process; there is one of these, in AppContainer.
 */
class ConnectivityMonitor(context: Context) {

    private val manager = context.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(hasInternet(manager.getNetworkCapabilities(manager.activeNetwork)))
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _isOnline.value = hasInternet(caps)
            }

            override fun onLost(network: Network) {
                _isOnline.value = false
            }
        })
    }

    private fun hasInternet(caps: NetworkCapabilities?): Boolean =
        caps != null &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/**
 * Emits once each time this goes from false to true: coming back online, or regaining a
 * valid token. The starting value doesn't count, so starting online is not a reconnection.
 */
fun Flow<Boolean>.becameTrue(): Flow<Unit> =
    distinctUntilChanged().drop(1).filter { it }.map { }
