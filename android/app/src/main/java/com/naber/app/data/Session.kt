package com.naber.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Oturum bilgisi cihazda saklanir; uygulama kapanip acildiginda
 * kullanici tekrar giris yapmak zorunda kalmaz.
 *
 * Sunucu adresi uygulamaya gomulu gelir; kullaniciya sorulmaz.
 * Yalnizca yonetim panelinden degistirilebilir.
 */
class Session(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("naber_session", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER
        set(value) {
            val normalized = normalizeBaseUrl(value)
            prefs.edit().putString(KEY_BASE_URL, normalized.ifBlank { DEFAULT_SERVER }).apply()
        }

    val isCustomServer: Boolean
        get() = baseUrl != DEFAULT_SERVER

    fun resetServer() {
        prefs.edit().remove(KEY_BASE_URL).apply()
    }

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
                .put("naber_email", value.naberEmail)
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

    /** Profil fotografi degistiginde hemen gostermek icin yerel kopya. */
    var localAvatar: String
        get() = prefs.getString(KEY_LOCAL_AVATAR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LOCAL_AVATAR, value).apply()

    val isLoggedIn: Boolean
        get() = token.isNotEmpty()

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).remove(KEY_LOCAL_AVATAR).apply()
    }

    companion object {
        /** Uygulamanin bagli oldugu WordPress sunucusu. */
        const val DEFAULT_SERVER = "https://nhentr.riaslink.fun"

        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER = "user"
        private const val KEY_PUSH = "push_token"
        private const val KEY_LOCAL_AVATAR = "local_avatar"

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
