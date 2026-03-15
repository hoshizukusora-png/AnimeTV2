package com.animatv.player.extra

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.*

class TLSSocketFactory : SSLSocketFactory() {

    private val delegate: SSLSocketFactory

    init {
        delegate = buildSocketFactory()
    }

    private fun buildSocketFactory(): SSLSocketFactory {
        val protocols = when {
            Build.VERSION.SDK_INT < 20 -> arrayOf("TLSv1", "TLSv1.1", "TLSv1.2")
            Build.VERSION.SDK_INT < 29 -> arrayOf("TLSv1.1", "TLSv1.2")
            else -> arrayOf("TLSv1.2", "TLSv1.3")
        }

        for (protocol in protocols) {
            try {
                val ctx = SSLContext.getInstance(protocol)
                ctx.init(null, arrayOf<TrustManager>(trustAllManager()), SecureRandom())
                Log.d("TLSSocketFactory", "Using protocol: $protocol")
                return ctx.socketFactory
            } catch (e: Exception) {
                Log.w("TLSSocketFactory", "Protocol $protocol failed: ${e.message}")
            }
        }

        // absolute fallback
        return SSLSocketFactory.getDefault() as SSLSocketFactory
    }

    @SuppressLint("TrustAllX509TrustManager", "CustomX509TrustManager")
    private fun trustAllManager(): X509TrustManager {
        return HttpsTrustManager()
    }

    fun trustAllHttps(): TLSSocketFactory {
        try {
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
            HttpsURLConnection.setDefaultSSLSocketFactory(delegate)
        } catch (e: Exception) {
            Log.w("TLSSocketFactory", "trustAllHttps failed: ${e.message}")
        }
        return this
    }

    fun getTrustManager(): X509TrustManager = HttpsTrustManager()

    private fun patch(socket: Socket): Socket {
        if (socket is SSLSocket) {
            try {
                val want = mutableListOf<String>()
                val supported = socket.supportedProtocols.toList()
                listOf("TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1").forEach {
                    if (supported.contains(it)) want.add(it)
                }
                if (want.isNotEmpty()) socket.enabledProtocols = want.toTypedArray()
            } catch (e: Exception) {
                Log.w("TLSSocketFactory", "patch failed: ${e.message}")
            }
        }
        return socket
    }

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    @Throws(IOException::class)
    override fun createSocket() = patch(delegate.createSocket())

    @Throws(IOException::class)
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean) =
        patch(delegate.createSocket(s, host, port, autoClose))

    @Throws(IOException::class)
    override fun createSocket(host: String, port: Int) =
        patch(delegate.createSocket(host, port))

    @Throws(IOException::class)
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int) =
        patch(delegate.createSocket(host, port, localHost, localPort))

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress, port: Int) =
        patch(delegate.createSocket(host, port))

    @Throws(IOException::class)
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int) =
        patch(delegate.createSocket(address, port, localAddress, localPort))
}
