package dev.ahmad.routerbattery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class RouterException(message: String) : Exception(message)

data class Reading(val percent: Int, val charging: String, val rx: String?, val tx: String?)

object RouterApi {
    private const val GET = "/goform/goform_get_cmd_process"
    private const val SET = "/goform/goform_set_cmd_process"

    // Session counters: the usual ZTE field names, NOT yet confirmed on this router.
    // If the widget shows no data line, change these two.
    private const val F_RX = "realtime_rx_bytes"
    private const val F_TX = "realtime_tx_bytes"

    fun fetch(ctx: Context, ip: String, password: String): Reading {
        val base = "http://$ip"
        val net = wifiNetwork(ctx)

        // 1. fresh single-use LD token
        val (ldBody, ldCookie) = post(net, base, GET, "cmd=LD", null)
        val ld = JSONObject(ldBody).optString("LD")
        if (ld.isEmpty()) throw RouterException("No login token")

        // 2. login: SHA256(SHA256(password) + LD)
        val hash = sha256(sha256(password) + ld)
        val (loginBody, loginCookie) =
            post(net, base, SET, "isTest=false&goformId=LOGIN&password=$hash", ldCookie)
        when (val result = JSONObject(loginBody).optString("result")) {
            "0", "4" -> {}
            "3" -> throw RouterException("Wrong password")
            "5" -> throw RouterException("Login locked")
            "2" -> throw RouterException("Already logged in")
            else -> throw RouterException("Login failed ($result)")
        }
        val cookie = loginCookie ?: ldCookie

        // 3. read values
        fun cmd(name: String): String =
            JSONObject(post(net, base, GET, "cmd=$name", cookie).first).optString(name)

        val pct = cmd("battery_value").toIntOrNull() ?: throw RouterException("No battery value")
        val charging = cmd("battery_charging").ifEmpty { "0" }
        val rx = cmd(F_RX).ifEmpty { null }
        val tx = cmd(F_TX).ifEmpty { null }
        return Reading(pct, charging, rx, tx)
    }

    /** Returns (body, stok cookie "stok=XXXX" or null). */
    private fun post(net: Network?, base: String, path: String, body: String, cookie: String?): Pair<String, String?> {
        val url = URL(base + path)
        val conn = (net?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            conn.setRequestProperty("Referer", "$base/index.html")
            cookie?.let { conn.setRequestProperty("Cookie", it) }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val stok = conn.headerFields["Set-Cookie"]
                ?.firstOrNull { it.startsWith("stok=") }?.substringBefore(';')
            return text to stok
        } finally {
            conn.disconnect()
        }
    }

    // Bind to Wi-Fi so the request goes to the router even if mobile data is preferred.
    @Suppress("DEPRECATION")
    private fun wifiNetwork(ctx: Context): Network? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        return cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02X".format(it) }
}
