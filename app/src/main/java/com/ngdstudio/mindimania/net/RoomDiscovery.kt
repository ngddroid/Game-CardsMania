package com.ngdstudio.mindimania.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.util.ArrayDeque

data class FoundRoom(
    val key: String,
    val host: String,
    val port: Int,
    val name: String,
    val room: RoomState?,
)

/** Advertises a hosted room and discovers rooms on the same Wi-Fi with Android NSD (mDNS). */
class RoomDiscovery(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var probeJob: Job? = null

    private val _rooms = MutableStateFlow<List<FoundRoom>>(emptyList())
    val rooms: StateFlow<List<FoundRoom>> = _rooms
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    // ------------------------------------------------------------------ advertise

    fun advertise(hostName: String, port: Int) {
        stopAdvertising()
        val info = NsdServiceInfo().apply {
            serviceName = "CM ${hostName.take(20)} ${(1000..9999).random()}"
            serviceType = Net.SERVICE_TYPE
            setPort(port)
            setAttribute("n", hostName.take(20))
            setAttribute("v", Net.PROTOCOL.toString())
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        regListener = l
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stopAdvertising() {
        regListener?.let { runCatching { nsd.unregisterService(it) } }
        regListener = null
    }

    // ------------------------------------------------------------------ discover

    fun startDiscovery() {
        if (discListener != null) return
        _rooms.value = emptyList()
        multicastLock = runCatching {
            wifi.createMulticastLock("cardsmania-discovery").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                _searching.value = true
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceType.contains("cardsmania")) return
                scope.launch(Dispatchers.Main) { enqueueResolve(info) }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                scope.launch(Dispatchers.Main) { _rooms.value = _rooms.value.filter { it.key != info.serviceName } }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _searching.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _searching.value = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discListener = l
        runCatching { nsd.discoverServices(Net.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
        // keep room details (mode, players) fresh
        probeJob = scope.launch {
            while (isActive) {
                delay(3500)
                for (r in _rooms.value) {
                    val st = RoomClient.probe(r.host, r.port, scope)
                    _rooms.value = if (st == null) _rooms.value.filter { it.key != r.key }
                    else _rooms.value.map { if (it.key == r.key) it.copy(room = st) else it }
                }
            }
        }
    }

    fun stopDiscovery() {
        probeJob?.cancel()
        probeJob = null
        discListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discListener = null
        pending.clear()
        resolving = false
        _searching.value = false
        runCatching { multicastLock?.release() }
        multicastLock = null
    }

    private fun enqueueResolve(info: NsdServiceInfo) {
        pending.add(info)
        resolveNext()
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolving) return
        val next = pending.poll() ?: return
        resolving = true
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                scope.launch(Dispatchers.Main) {
                    resolving = false
                    if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                        delay(300)
                        pending.add(serviceInfo)
                    }
                    resolveNext()
                }
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                val addr = if (Build.VERSION.SDK_INT >= 34) {
                    info.hostAddresses.firstOrNull { it is Inet4Address }
                } else info.host
                val host = addr?.hostAddress
                val name = info.attributes["n"]?.let { String(it) } ?: info.serviceName
                scope.launch(Dispatchers.Main) {
                    resolving = false
                    if (host != null && addr is Inet4Address) {
                        val room = RoomClient.probe(host, info.port, scope)
                        val found = FoundRoom(info.serviceName, host, info.port, name, room)
                        _rooms.value = _rooms.value.filter { it.key != found.key && !(it.host == host && it.port == info.port) } + found
                    }
                    resolveNext()
                }
            }
        }
        runCatching { nsd.resolveService(next, listener) }.onFailure {
            resolving = false
            resolveNext()
        }
    }
}
