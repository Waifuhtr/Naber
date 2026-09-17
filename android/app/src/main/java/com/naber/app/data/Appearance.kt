package com.naber.app.data

import android.content.Context
import com.naber.app.ui.theme.NaberColors

/**
 * Tema tercihi (koyu / acik).
 *
 * Tercih cihazda saklanir; sunucuya gitmez, cunku ayni hesabi farkli
 * telefonlarda farkli temayla kullanmak isteyen olabilir.
 */
object Appearance {

    private const val PREFS = "naber_appearance"
    private const val KEY_DARK = "dark"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Uygulama acilirken cagrilir; kayitli tercih yoksa koyu tema. */
    fun load(context: Context) {
        NaberColors.dark = prefs(context).getBoolean(KEY_DARK, true)
    }

    fun isDark(): Boolean = NaberColors.dark

    fun setDark(context: Context, dark: Boolean) {
        NaberColors.dark = dark
        prefs(context).edit().putBoolean(KEY_DARK, dark).apply()
    }
}
