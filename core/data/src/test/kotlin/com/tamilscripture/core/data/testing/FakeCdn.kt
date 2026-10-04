package com.tamilscripture.core.data.testing

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A local stand-in for the CDN origins (M0-14), on the JDK's own HTTP server: files by
 * path, Range requests, a request log, and faults to inject (an error status or a
 * connection dropped half-way).
 */
class FakeCdn {
    sealed interface Fault {
        data class Status(val code: Int) : Fault
        /** Sends the headers and this many bytes of the body, then closes the connection. */
        data class DropAfter(val bytes: Int) : Fault
    }

    private val files = ConcurrentHashMap<String, ByteArray>()
    private val faults = ConcurrentHashMap<String, Fault>()
    val requests = CopyOnWriteArrayList<String>()
    private var server: HttpServer? = null

    /** Kept after [stop], so a client made later finds nothing listening there. */
    lateinit var base: String
        private set

    fun put(path: String, body: ByteArray) { files[path.trimStart('/')] = body }
    fun put(path: String, body: String) = put(path, body.toByteArray())
    fun fault(path: String, fault: Fault?) { if (fault == null) faults.remove(path.trimStart('/')) else faults[path.trimStart('/')] = fault }

    fun start(): FakeCdn {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex ->
            val path = ex.requestURI.path.trimStart('/')
            requests += path
            val body = files[path]
            when (val f = faults[path]) {
                is Fault.Status -> { ex.sendResponseHeaders(f.code, -1); ex.close(); return@createContext }
                else -> Unit
            }
            if (body == null) { ex.sendResponseHeaders(404, -1); ex.close(); return@createContext }
            val range = ex.requestHeaders.getFirst("Range")?.let { Regex("""bytes=(\d+)-(\d*)""").find(it) }
            val from = range?.groupValues?.get(1)?.toInt() ?: 0
            val to = range?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.toInt() ?: (body.size - 1)
            val part = body.copyOfRange(from, to + 1)
            if (range != null) ex.responseHeaders.add("Content-Range", "bytes $from-$to/${body.size}")
            ex.responseHeaders.add("Accept-Ranges", "bytes")
            ex.sendResponseHeaders(if (range != null) 206 else 200, part.size.toLong())
            val drop = faults[path] as? Fault.DropAfter
            ex.responseBody.use { out ->
                if (drop != null) { out.write(part, 0, minOf(drop.bytes, part.size)); out.flush(); throw java.io.IOException("dropped") }
                out.write(part)
            }
        }
        s.start()
        server = s
        base = "http://127.0.0.1:${s.address.port}/"
        return this
    }

    fun stop() { server?.stop(0); server = null }
}
