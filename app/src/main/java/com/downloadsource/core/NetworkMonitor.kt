package com.downloadsource.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NetworkMonitor {
    private val _isWifi = MutableStateFlow(true)
    val isWifi: StateFlow<Boolean> = _isWifi.asStateFlow()

    fun start(ctx: Context) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                if (_isWifi.value && !wifi) {
                    DownloadManager.tasks.value.filter { it.status == TaskStatus.Running }.forEach {
                        DownloadManager.pause(ctx, it.id)
                    }
                    Toast.makeText(ctx, "已切至移动数据，下载已暂停 (￣▽￣)", Toast.LENGTH_LONG).show()
                }
                _isWifi.value = wifi
            }
            override fun onLost(network: Network) { _isWifi.value = false }
        })
    }
}
