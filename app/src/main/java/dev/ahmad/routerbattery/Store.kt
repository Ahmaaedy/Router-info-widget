package dev.ahmad.routerbattery

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class Snapshot(
    val percent: Int?,
    val charging: String,
    val rx: String?,
    val tx: String?,
    val time: Long,
    val error: String?,
    val busy: Boolean,
)

object Store {
    // Router IP + password: encrypted at rest (Android Keystore).
    private fun secure(c: Context) = EncryptedSharedPreferences.create(
        c, "secure",
        MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    // Last reading: not sensitive.
    private fun plain(c: Context) = c.getSharedPreferences("state", Context.MODE_PRIVATE)

    fun saveCreds(c: Context, ip: String, password: String) {
        val e = secure(c).edit().putString("ip", ip)
        if (password.isNotBlank()) e.putString("pw", password)
        e.apply()
    }

    fun ip(c: Context): String = secure(c).getString("ip", "192.168.0.1") ?: "192.168.0.1"
    fun password(c: Context): String = secure(c).getString("pw", "") ?: ""

    fun saveReading(c: Context, r: Reading) {
        plain(c).edit()
            .putInt("pct", r.percent).putString("chg", r.charging)
            .putString("rx", r.rx).putString("tx", r.tx)
            .putLong("time", System.currentTimeMillis())
            .remove("err").putBoolean("busy", false).apply()
    }

    fun saveError(c: Context, msg: String) {
        plain(c).edit().putString("err", msg).putBoolean("busy", false).apply()
    }

    fun setBusy(c: Context, busy: Boolean) {
        plain(c).edit().putBoolean("busy", busy).apply()
    }

    fun snapshot(c: Context): Snapshot {
        val p = plain(c)
        return Snapshot(
            percent = if (p.contains("pct")) p.getInt("pct", 0) else null,
            charging = p.getString("chg", "0") ?: "0",
            rx = p.getString("rx", null),
            tx = p.getString("tx", null),
            time = p.getLong("time", 0),
            error = p.getString("err", null),
            busy = p.getBoolean("busy", false),
        )
    }
}
