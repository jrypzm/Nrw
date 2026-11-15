package app.years.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

sealed class Hit {
    class AppHit(val app: App, val score: Int) : Hit()
    class Person(val name: String, val phone: String?, val uri: Uri) : Hit()
    class Setting(val title: String, val action: String) : Hit()
    class Answer(val title: String, val value: String) : Hit()
    class Web(val query: String) : Hit()
}

/** Search that answers as you type. Apps come from memory; people from the phone's contacts; nothing leaves the phone. */
object Search {
    /** Ranks apps: the name starts with it, a word starts with it, the initials match, it's inside the name, its letters appear in order. */
    fun apps(q: String, limit: Int = 8): List<Hit.AppHit> {
        val n = App.fold(q).trim()
        if (n.isEmpty()) return emptyList()
        val out = ArrayList<Hit.AppHit>()
        for (a in Apps.visible) {
            val s = when {
                a.norm == n -> 1000
                a.norm.startsWith(n) -> 900 - a.norm.length
                a.norm.split(' ', '-', '.').any { it.startsWith(n) } -> 700 - a.norm.length
                n.length >= 2 && a.initials.startsWith(n) -> 600
                a.norm.contains(n) -> 500 - a.norm.indexOf(n)
                n.length >= 3 && subsequence(n, a.norm) -> 200 - a.norm.length
                else -> 0
            }
            if (s > 0) out += Hit.AppHit(a, s)
        }
        out.sortByDescending { it.score }
        return out.take(limit)
    }

    private fun subsequence(q: String, s: String): Boolean { var i = 0; for (c in s) if (i < q.length && c == q[i]) i++; return i == q.length }

    fun people(c: Context, q: String, limit: Int = 4): List<Hit.Person> {
        if (q.trim().length < 2 || !Prefs.get(c).people || c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q.trim()))
        val out = ArrayList<Hit.Person>()
        runCatching {
            c.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.HAS_PHONE_NUMBER), null, null, null)?.use { cur ->
                while (cur.moveToNext() && out.size < limit) {
                    val id = cur.getLong(0); val lookup = cur.getString(1); val name = cur.getString(2) ?: continue
                    val phone = if (cur.getInt(3) > 0) c.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?", arrayOf(id.toString()), null)?.use { p -> if (p.moveToFirst()) p.getString(0) else null } else null
                    out += Hit.Person(name, phone, ContactsContract.Contacts.getLookupUri(id, lookup))
                }
            }
        }
        return out
    }

    /** Settings you can jump straight into. */
    private val SETTINGS = listOf(
        "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS, "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS, "Mobile data" to Settings.ACTION_DATA_ROAMING_SETTINGS,
        "Display and brightness" to Settings.ACTION_DISPLAY_SETTINGS, "Sound and vibration" to Settings.ACTION_SOUND_SETTINGS, "Notifications" to "android.settings.ALL_APPS_NOTIFICATION_SETTINGS",
        "Battery" to Intent_BATTERY, "Apps" to Settings.ACTION_APPLICATION_SETTINGS, "Location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "Security and privacy" to Settings.ACTION_SECURITY_SETTINGS, "Storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "Date and time" to Settings.ACTION_DATE_SETTINGS,
        "Language and keyboard" to Settings.ACTION_LOCALE_SETTINGS, "Accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS, "Do not disturb" to "android.settings.ZEN_MODE_SETTINGS",
        "Hotspot and tethering" to "android.settings.TETHER_SETTINGS", "NFC" to Settings.ACTION_NFC_SETTINGS, "Airplane mode" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
        "Wallpaper" to "android.intent.action.SET_WALLPAPER", "Default apps" to Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, "Developer options" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        "About phone" to Settings.ACTION_DEVICE_INFO_SETTINGS, "Accounts" to Settings.ACTION_SYNC_SETTINGS, "Phone settings" to Settings.ACTION_SETTINGS,
    )
    private const val Intent_BATTERY = "android.intent.action.POWER_USAGE_SUMMARY"
    private val SETTING_WORDS = mapOf("wifi" to "Wi-Fi", "wlan" to "Wi-Fi", "internet" to "Wi-Fi", "brightness" to "Display and brightness", "screen" to "Display and brightness", "dark" to "Display and brightness",
        "volume" to "Sound and vibration", "ringtone" to "Sound and vibration", "vibrate" to "Sound and vibration", "gps" to "Location", "privacy" to "Security and privacy", "password" to "Security and privacy",
        "space" to "Storage", "time" to "Date and time", "keyboard" to "Language and keyboard", "dnd" to "Do not disturb", "hotspot" to "Hotspot and tethering", "flight" to "Airplane mode", "plane" to "Airplane mode",
        "data" to "Mobile data", "default" to "Default apps", "developer" to "Developer options", "settings" to "Phone settings")

    fun settings(q: String): List<Hit.Setting> {
        val n = App.fold(q).trim(); if (n.length < 2) return emptyList()
        val named = if (n.length >= 3) SETTING_WORDS.entries.filter { it.key.startsWith(n) }.map { it.value } else emptyList()
        return SETTINGS.filter { (t, _) -> App.fold(t).split(' ', '-').any { it.startsWith(n) } || t in named }.take(3).map { Hit.Setting(it.first, it.second) }
    }

    /** Arithmetic ("12 × 8.5 + 3") and units ("5 km in miles", "70 kg", "30 c"). */
    fun answer(q: String): Hit.Answer? {
        val t = q.trim(); if (t.isEmpty()) return null
        convert(t)?.let { return it }
        if (!t.any { it.isDigit() } || !t.any { it in "+-*/×÷^%()" }) return null
        val v = Calc.eval(t) ?: return null
        return Hit.Answer("Calculator", fmt(v))
    }

    private val UNITS: Map<String, Pair<String, (Double) -> Double>> = mapOf(
        "km" to ("mi" to { v -> v * 0.621371 }), "mi" to ("km" to { v -> v / 0.621371 }), "mile" to ("km" to { v -> v / 0.621371 }), "miles" to ("km" to { v -> v / 0.621371 }),
        "kg" to ("lb" to { v -> v * 2.20462 }), "lb" to ("kg" to { v -> v / 2.20462 }), "lbs" to ("kg" to { v -> v / 2.20462 }),
        "cm" to ("in" to { v -> v / 2.54 }), "in" to ("cm" to { v -> v * 2.54 }), "inch" to ("cm" to { v -> v * 2.54 }), "m" to ("ft" to { v -> v * 3.28084 }), "ft" to ("m" to { v -> v / 3.28084 }),
        "c" to ("°F" to { v -> v * 9 / 5 + 32 }), "°c" to ("°F" to { v -> v * 9 / 5 + 32 }), "f" to ("°C" to { v -> (v - 32) * 5 / 9 }), "°f" to ("°C" to { v -> (v - 32) * 5 / 9 }),
        "l" to ("gal" to { v -> v * 0.264172 }), "gal" to ("l" to { v -> v / 0.264172 }), "g" to ("oz" to { v -> v / 28.3495 }), "oz" to ("g" to { v -> v * 28.3495 }),
    )
    private val unitRx = Regex("^\\s*(-?[0-9]+(?:[.,][0-9]+)?)\\s*([a-z°]+)(?:\\s+(?:in|to)\\s+[a-z°]+)?\\s*$", RegexOption.IGNORE_CASE)
    private fun convert(t: String): Hit.Answer? {
        val m = unitRx.matchEntire(t) ?: return null
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val (to, f) = UNITS[m.groupValues[2].lowercase()] ?: return null
        return Hit.Answer("Conversion", "${fmt(f(v), 2)} $to")
    }

    fun fmt(v: Double, places: Int = 6): String {
        if (v.isNaN() || v.isInfinite()) return "—"
        val bd = BigDecimal(v).round(MathContext(12)).setScale(places, RoundingMode.HALF_UP).stripTrailingZeros()
        val s = bd.toPlainString()
        val (i, f) = s.split('.').let { it[0] to it.getOrNull(1) }
        val neg = i.startsWith("-"); val digits = i.trimStart('-')
        val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
        return (if (neg) "-" else "") + grouped + (f?.let { ".$it" } ?: "")
    }
}

/** A small, careful calculator: + − × ÷ ^ %, brackets, decimals. Returns null for anything it can't read. */
object Calc {
    fun eval(src: String): Double? = runCatching {
        val s = src.replace('×', '*').replace('÷', '/').replace('−', '-').replace(',', '.').replace(" ", "")
        val p = P(s); val v = p.expr(); if (p.i != s.length) null else v
    }.getOrNull()?.takeIf { it.isFinite() }

    private class P(val s: String) {
        var i = 0
        fun peek() = if (i < s.length) s[i] else '\u0000'
        fun expr(): Double { var v = term(); while (true) { when (peek()) { '+' -> { i++; v += term() }; '-' -> { i++; v -= term() }; else -> return v } } }
        fun term(): Double { var v = pow(); while (true) { when (peek()) { '*' -> { i++; v *= pow() }; '/' -> { i++; v /= pow() }; else -> return v } } }
        fun pow(): Double { val b = unary(); return if (peek() == '^') { i++; Math.pow(b, pow()) } else b }
        fun unary(): Double = if (peek() == '-') { i++; -unary() } else if (peek() == '+') { i++; unary() } else post()
        fun post(): Double { var v = atom(); while (peek() == '%') { i++; v /= 100 }; return v }
        fun atom(): Double {
            if (peek() == '(') { i++; val v = expr(); require(peek() == ')'); i++; return v }
            val st = i; while (peek().isDigit() || peek() == '.') i++
            require(i > st); return s.substring(st, i).toDouble()
        }
    }
}
