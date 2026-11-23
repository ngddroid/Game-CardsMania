package com.ngdstudio.mindimania.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One TCP connection carrying newline-delimited JSON [NetMsg]s.
 * Reads on an IO coroutine (with a line-size limit), writes through a queue, and sends a heartbeat.
 */
class Connection(
    private val socket: Socket,
    private val scope: CoroutineScope,
    private val maxLine: Int,
) {
    private val outbox = Channel<String>(capacity = 512)
    private val closed = AtomicBoolean(false)
    private var jobs = mutableListOf<Job>()
    val remoteAddress: String = socket.inetAddress?.hostAddress ?: "?"

    init {
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.soTimeout = Net.TIMEOUT_MS
    }

    /** Starts reading/writing. [onMessage] is called on an IO thread for each decoded message. */
    fun start(onMessage: suspend (NetMsg) -> Unit, onClosed: () -> Unit) {
        jobs += scope.launch(Dispatchers.IO) {
            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8).buffered(32 * 1024)
            try {
                for (line in outbox) {
                    writer.write(line)
                    writer.write("\n")
                    if (outbox.isEmpty) writer.flush()
                }
            } catch (_: Throwable) {
            } finally {
                close()
            }
        }
        jobs += scope.launch(Dispatchers.IO) {
            while (isActive && !closed.get()) {
                delay(Net.HEARTBEAT_MS)
                send(NetMsg.Ping)
            }
        }
        jobs += scope.launch(Dispatchers.IO) {
            val reader = InputStreamReader(socket.getInputStream(), Charsets.UTF_8).buffered(32 * 1024)
            val sb = StringBuilder()
            try {
                while (isActive && !closed.get()) {
                    val ch = reader.read()
                    if (ch < 0) break
                    if (ch == '\n'.code) {
                        val line = sb.toString()
                        sb.setLength(0)
                        if (line.isBlank()) continue
                        val msg = Net.decode(line) ?: continue
                        if (msg is NetMsg.Ping) continue
                        onMessage(msg)
                    } else {
                        sb.append(ch.toChar())
                        if (sb.length > maxLine) break
                    }
                }
            } catch (_: Throwable) {
            } finally {
                close()
                onClosed()
            }
        }
    }

    fun send(msg: NetMsg) = sendRaw(Net.encode(msg))

    fun sendRaw(line: String) {
        if (closed.get()) return
        if (outbox.trySend(line).isFailure) close() // peer is not reading: drop it
    }

    val isOpen: Boolean get() = !closed.get()

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        outbox.close()
        runCatching { socket.close() }
    }

    companion object {
        /** Opens a client connection (blocking; call from IO). */
        fun connect(host: String, port: Int, scope: CoroutineScope, timeoutMs: Int = 4000): Connection {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), timeoutMs)
            return Connection(s, scope, Net.HOST_LINE_LIMIT)
        }
    }
}
