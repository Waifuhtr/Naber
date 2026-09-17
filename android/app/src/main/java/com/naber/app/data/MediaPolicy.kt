package com.naber.app.data

import android.content.Context
import com.naber.app.ui.isMeteredConnection

/**
 * Gorsellerin ne zaman kendiliginden indirilecegi.
 *
 * Varsayilan "yalnizca wifi": mobil veride sohbeti acmak, gormek bile
 * istemedigin gorselleri indirip kotayi yemesin. Indirilmeyen gorselin
 * yerinde mesajla gelen bulanik on izleme durur, uzerine dokunulunca
 * asil dosya iner.
 */
object MediaPolicy {

    const val ALWAYS = "her-zaman"
    const val WIFI_ONLY = "wifi"
    const val MANUAL = "elle"

    private const val PREFS = "naber_medya"
    private const val KEY_MODE = "otomatik_indirme"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(context: Context): String =
        prefs(context).getString(KEY_MODE, WIFI_ONLY) ?: WIFI_ONLY

    fun setMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_MODE, mode).apply()
    }

    /** Su anki baglantida gorsel kendiliginden inmeli mi? */
    fun autoDownload(context: Context): Boolean = when (mode(context)) {
        ALWAYS -> true
        MANUAL -> false
        else -> !isMeteredConnection(context)
    }

    fun label(mode: String): String = when (mode) {
        ALWAYS -> "Her zaman"
        MANUAL -> "Elle indir"
        else -> "Yalnizca wifi"
    }
}
