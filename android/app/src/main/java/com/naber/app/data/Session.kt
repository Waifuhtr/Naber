package com.naber.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Oturum bilgisi cihazda saklanir; uygulama kapanip acildiginda
 * kullanici tekrar giris yapmak zorunda kalmaz.
 */
class Session(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("naber_session", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BASE_URL, normalizeBaseUrl(value)).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var user: User?
        get() {
            val raw = prefs.getString(KEY_USER, null) ?: return null
            return runCatching { User.from(JSONObject(raw)) }.getOrNull()
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_USER).apply()
                return
            }
            val json = JSONObject()
                .put("id", value.id)
                .put("username", value.username)
                .put("display_name", value.displayName)
                .put("avatar", value.avatar)
                .put("about", value.about)
                .put("last_seen", value.lastSeen)
                .put("online", value.online)
                .put("is_admin", value.isAdmin)
            prefs.edit().putString(KEY_USER, json.toString()).apply()
        }

    var pushToken: String
        get() = prefs.getString(KEY_PUSH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PUSH, value).apply()

    val isLoggedIn: Boolean
        get() = token.isNotEmpty() && baseUrl.isNotEmpty()

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER = "user"
        private const val KEY_PUSH = "push_token"

        /** "ornek.com" -> "https://ornek.com" ; sondaki / temizlenir. */
        fun normalizeBaseUrl(input: String): String {
            var url = input.trim()
            if (url.isEmpty()) return ""
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            return url.trimEnd('/')
        }
    }
}
