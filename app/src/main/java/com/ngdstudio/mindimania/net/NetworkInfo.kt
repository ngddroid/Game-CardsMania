package com.ngdstudio.mindimania.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkInfo {

    enum class Link { WIFI, ETHERNET, HOTSPOT, NONE }

    /** How this phone is connected to a local network. */
    fun link(context: Context): Link {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> Link.WIFI
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> Link.ETHERNET
            hotspotAddress() != null -> Link.HOTSPOT
            else -> Link.NONE
        }
    }

    /** The phone's IPv4 address on the local network (Wi-Fi first, then a hotspot interface). */
    fun localIpv4(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val lp = cm.getLinkProperties(cm.activeNetwork)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true || caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) {
            lp?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress?.let { return it }
        }
        return interfaceAddress { it.startsWith("wlan") || it.startsWith("eth") } ?: hotspotAddress()
    }

    private fun hotspotAddress(): String? = interfaceAddress { it.startsWith("ap") || it.startsWith("swlan") || it.startsWith("softap") || it.startsWith("wlan1") }

    private fun interfaceAddress(match: (String) -> Boolean): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && match(it.name) }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()
}
