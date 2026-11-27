package com.example.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object NetworkMonitor {
    // FIX: INTERNET capability only — do NOT require VALIDATED. A working
    // connection often has INTERNET but is not yet VALIDATED; gating on
    // VALIDATED made the app report "offline" and never sync.
    private fun hasInternet(caps: NetworkCapabilities?): Boolean {
        if (caps == null) return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isActiveNetworkOnline(cm: ConnectivityManager): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return hasInternet(caps)
    }

    fun isCurrentlyOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return isActiveNetworkOnline(cm)
    }

    // hasNetworkConnection reports ANY interface with INTERNET capability - not
    // necessarily the active/default route. Prefer isCurrentlyOnline for sync gating.
    fun hasNetworkConnection(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeCaps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
            return true
        }
        @Suppress("DEPRECATION")
        val allNetworks = runCatching { cm.allNetworks }.getOrNull().orEmpty()
        return allNetworks.any { net ->
            cm.getNetworkCapabilities(net)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }
    }

    fun observeOnline(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        trySend(isActiveNetworkOnline(cm))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
            val caps = cm.getNetworkCapabilities(network)
            trySend(if (caps != null) hasInternet(caps) else isActiveNetworkOnline(cm))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val active = cm.activeNetwork
                val online = if (active == null || active == network) {
                    hasInternet(networkCapabilities)
                } else {
                    isActiveNetworkOnline(cm)
                }
                trySend(online)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)

        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged()
}
