package com.naber.app.data

/**
 * Gonderilen gorsellerin cihazdaki adresini tutar.
 * Sunucudan gelen imzali adres inene kadar (ve indikten sonra da) gorsel
 * aninda ekranda gorunsun diye kullanilir.
 */
object LocalMedia {

    private val byMediaId = HashMap<Int, String>()
    private val byClientId = HashMap<String, String>()

    fun remember(clientId: String, uri: String) {
        if (clientId.isNotBlank()) byClientId[clientId] = uri
    }

    fun link(mediaId: Int, clientId: String) {
        val uri = byClientId[clientId] ?: return
        if (mediaId > 0) byMediaId[mediaId] = uri
    }

    fun rememberMedia(mediaId: Int, uri: String) {
        if (mediaId > 0) byMediaId[mediaId] = uri
    }

    fun uriFor(mediaId: Int, clientId: String): String? =
        byMediaId[mediaId] ?: byClientId[clientId]

    fun clear() {
        byMediaId.clear()
        byClientId.clear()
    }
}
