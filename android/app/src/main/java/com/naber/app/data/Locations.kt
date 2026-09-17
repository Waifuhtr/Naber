package com.naber.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Konum mesajlari.
 *
 * Konum, mesaj govdesinde "enlem,boylam" olarak tasinir; ayri bir tablo
 * ya da alan gerekmez. Bicim her zaman noktali yazilir: Turkce yerel
 * ayarda ondalik ayraci virgul oldugu icin "41,015137" gibi bir deger
 * sunucuda ikiye bolunur ve konum bozulurdu.
 */
fun formatLocation(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "%.6f,%.6f", latitude, longitude)

/** "enlem,boylam" govdesini cozer; bozuksa null. */
fun parseLocation(body: String): Pair<Double, Double>? {
    val parts = body.split(',')
    if (parts.size != 2) return null
    val latitude = parts[0].trim().toDoubleOrNull() ?: return null
    val longitude = parts[1].trim().toDoubleOrNull() ?: return null
    if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null
    return latitude to longitude
}

/** Harita uygulamasinda acmak icin adres. */
fun mapsUri(latitude: Double, longitude: Double): Uri {
    val point = formatLocation(latitude, longitude)
    return Uri.parse("geo:$point?q=$point")
}

object Locations {

    /**
     * Cihazin su anki konumu.
     *
     * Once saglayicilarin bildigi son konuma bakilir; yoksa tek seferlik
     * guncelleme beklenir. Konum servisi kapaliysa ya da sure asilirsa
     * null doner; cagiran taraf kullaniciya bunu soyler.
     *
     * Izin kontrolu cagiran tarafta yapilir.
     */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context, timeoutMillis: Long = 10_000): Pair<Double, Double>? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        lastKnown(manager)?.let { return it.latitude to it.longitude }

        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) {
                            continuation.resume(location.latitude to location.longitude)
                        }
                    }

                    // Eski Android surumlerinde bu uc yontemin varsayilani
                    // yok; yazilmazsa cihazda cagrildiklarinda uygulama coker.
                    @Deprecated("Eski API uyumlulugu icin duruyor")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                    }

                    override fun onProviderEnabled(provider: String) {}

                    override fun onProviderDisabled(provider: String) {}
                }

                runCatching { manager.requestLocationUpdates(provider, 0L, 0f, listener) }
                    .onFailure { if (continuation.isActive) continuation.resume(null) }

                continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            // Iki saglayici da biliyorsa yeni olan alinir.
            .maxByOrNull { it.time }
    }
}
