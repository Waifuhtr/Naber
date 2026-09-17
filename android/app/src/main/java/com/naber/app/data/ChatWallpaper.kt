package com.naber.app.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.naber.app.ui.theme.NaberColors

/**
 * Sohbet arka plani.
 *
 * Secim sohbete ozeldir ve yalnizca bu cihazda saklanir: arka plan
 * gorunusle ilgilidir, karsi tarafi ilgilendirmez ve sunucuya
 * gonderilmesi gereksiz veri olurdu.
 */
object ChatWallpaper {

    private const val PREFS = "naber_wallpaper"

    /**
     * Hazir arka planlar. Koyu ve acik tema icin ayri renk tutulur;
     * yoksa acik temada koyu bir arka plan uzerine koyu yazi gelirdi.
     */
    data class Preset(
        val id: String,
        val label: String,
        private val darkColor: Long,
        private val lightColor: Long
    ) {
        /** 0 ise temanin kendi arka plani kullanilir. */
        val color: Color
            get() {
                val value = if (NaberColors.dark) darkColor else lightColor
                return if (value == 0L) NaberColors.Background else Color(value)
            }
    }

    val PRESETS = listOf(
        Preset("varsayilan", "Varsayilan", 0L, 0L),
        Preset("gece", "Gece mavisi", 0xFF0A1826, 0xFFE9F0F8),
        Preset("orman", "Orman", 0xFF0C1A14, 0xFFE8F3EC),
        Preset("kahve", "Kahve", 0xFF1A1410, 0xFFF4EDE5),
        Preset("mor", "Mor", 0xFF140F1F, 0xFFEFEAF8),
        Preset("kiremit", "Kiremit", 0xFF1D1210, 0xFFF8EBE7)
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun idFor(context: Context, conversationId: Int): String =
        prefs(context).getString(key(conversationId), null) ?: PRESETS.first().id

    fun of(context: Context, conversationId: Int): Preset {
        val id = idFor(context, conversationId)
        return PRESETS.firstOrNull { it.id == id } ?: PRESETS.first()
    }

    fun set(context: Context, conversationId: Int, presetId: String) {
        prefs(context).edit().putString(key(conversationId), presetId).apply()
    }

    private fun key(conversationId: Int) = "chat-$conversationId"
}
