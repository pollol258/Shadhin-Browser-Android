package com.shadhin.browser.network

import android.content.Context
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local HTTP CONNECT Tunnel / Loopback Proxy that resolves hostnames strictly via DoH.
 * Binds to WebView via AndroidX Webkit ProxyController when supported.
 */
class LocalDohProxy(
    private val dohResolver: DohResolver
) {

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val executor = Executors.newFixedThreadPool(64)
    var port: Int = 0
        private set

    @Synchronized
    fun start(): Int {
        if (isRunning.get()) return port
        try {
            val server = ServerSocket(0) // Bind to any free ephemeral port on loopback
            serverSocket = server
            port = server.localPort
            isRunning.set(true)

            executor.execute {
                while (isRunning.get() && !server.isClosed) {
                    try {
                        val clientSocket = server.accept()
                        executor.execute { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            }
        } catch (e: Exception) {
            port = 0
        }
        return port
    }

    @Synchronized
    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    /**
     * Configures AndroidX Webkit ProxyController to route traffic through this local proxy.
     * Returns true if ProxyController feature is supported and configured, false otherwise.
     */
    fun attachToWebView(context: Context): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            return false
        }
        val currentPort = if (isRunning.get()) port else start()
        if (currentPort <= 0) return false

        try {
            val proxyConfig = ProxyConfig.Builder()
                .addProxyRule("127.0.0.1:$currentPort")
                .addBypassRule("<-loopback>")
                .build()

            ProxyController.getInstance().setProxyOverride(
                proxyConfig,
                { /* Proxy applied */ },
                { /* Listener called on main thread */ }
            )
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun detachFromWebView() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            try {
                ProxyController.getInstance().clearProxyOverride({ }, { })
            } catch (_: Exception) {}
        }
        stop()
    }

    private fun handleClient(client: Socket) {
        var output: OutputStream? = null
        try {
            client.soTimeout = 10000
            val input = client.getInputStream()
            output = client.getOutputStream()

            // Read the initial HTTP request line
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val target = parts[1]

            if (method == "CONNECT") {
                // HTTPS Tunneling: host:port
                val hostPort = target.split(":")
                val host = hostPort[0]
                val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 443

                // Skip headers until empty line
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                }

                // Resolve target host via DoH (with fallback to secondary provider and system DNS)
                val resolvedAddresses = try {
                    dohResolver.lookup(host)
                } catch (e: Exception) {
                    emptyList()
                }

                val targetAddress = resolvedAddresses.firstOrNull()
                if (targetAddress == null) {
                    sendProxyError(output, "Unable to resolve $host")
                    return
                }

                val targetSocket = try {
                    Socket(targetAddress, port).apply { soTimeout = 15000 }
                } catch (e: Exception) {
                    sendProxyError(output, "Connection to $host:$port failed (${e.message})")
                    return
                }

                // Send 200 Connection Established to client
                output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.US_ASCII))
                output.flush()

                // Bidirectional pipe
                val clientIn = client.getInputStream()
                val clientOut = client.getOutputStream()
                val targetIn = targetSocket.getInputStream()
                val targetOut = targetSocket.getOutputStream()

                val t1 = Thread { pipeStream(clientIn, targetOut) }
                val t2 = Thread { pipeStream(targetIn, clientOut) }
                t1.start()
                t2.start()
                t1.join()
                t2.join()

                targetSocket.close()
            } else {
                sendProxyError(output, "Unsupported proxy method: $method")
            }
        } catch (e: Exception) {
            output?.let { sendProxyError(it, e.message ?: "Proxy connection aborted") }
        } finally {
            try {
                client.close()
            } catch (_: Exception) {}
        }
    }

    private fun sendProxyError(output: OutputStream, errorMessage: String) {
        try {
            val sanitized = errorMessage.replace("\r", " ").replace("\n", " ").take(120)
            val body = "Shadhin Proxy Error: $sanitized\r\n"
            val response = "HTTP/1.1 502 Bad Gateway\r\n" +
                "X-Shadhin-Proxy-Error: $sanitized\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n\r\n" +
                body
            output.write(response.toByteArray(Charsets.US_ASCII))
            output.flush()
        } catch (_: Exception) {}
    }

    private fun pipeStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        try {
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: Exception) {}
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var c: Int
        while (input.read().also { c = it } != -1) {
            if (c == '\r'.code) {
                val next = input.read()
                if (next == '\n'.code) break
                sb.append('\r').append(next.toChar())
            } else if (c == '\n'.code) {
                break
            } else {
                sb.append(c.toChar())
            }
        }
        return if (sb.isEmpty() && c == -1) null else sb.toString()
    }
}
