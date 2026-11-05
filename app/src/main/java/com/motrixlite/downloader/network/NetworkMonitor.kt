package com.motrixlite.downloader.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 监听默认网络状态，用于「仅 Wi-Fi 下载」策略和断网提示。
 */
class NetworkMonitor(context: Context, private val scope: CoroutineScope) {

    data class Status(
        val online: Boolean = false,
        val wifi: Boolean = false,
        val cellular: Boolean = false,
    )

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            update()
        }

        override fun onLost(network: Network) {
            update()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            update()
        }
    }

    init {
        scope.launch {
            runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }
        }
    }

    private fun update() {
        _status.value = readStatus()
    }

    private fun readStatus(): Status {
        return try {
            val network = connectivityManager.activeNetwork ?: return Status()
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return Status()
            val online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            Status(
                online = online,
                wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            )
        } catch (_: Exception) {
            Status()
        }
    }
}
