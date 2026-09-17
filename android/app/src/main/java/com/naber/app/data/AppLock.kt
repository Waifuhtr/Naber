package com.naber.app.data

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Uygulama kilidi (PIN).
 *
 * Telefon baskasinin eline gecerse sohbetler gorunmesin diye uygulama
 * her arka plana alindiginda kilitlenir; tekrar acilirken PIN sorulur.
 *
 * PIN acik metin olarak saklanmaz: kuruluma ozel rastgele bir tuz (salt)
 * ile SHA-256 ozeti tutulur. Bu, cihazi eline gecirmis siradan birine
 * karsi koruma saglar; kok (root) erisimi olan birine karsi yerel bir
 * PIN zaten koruma saglayamaz.
 */
object AppLock {

    private const val PREFS = "naber_lock"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    /**
     * Surec boyunca yasayan kilit durumu. Uygulama arka plana alininca
     * true yapilir, dogru PIN girilince false olur.
     *
     * Bastan true: uygulama sifirdan acildiginda da PIN sorulmali.
     */
    @Volatile
    private var locked = true

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Kullanici bir PIN belirlemis mi? */
    fun isEnabled(context: Context): Boolean =
        !prefs(context).getString(KEY_HASH, null).isNullOrBlank()

    /** Su an kilit ekrani gosterilmeli mi? */
    fun isLocked(context: Context): Boolean = isEnabled(context) && locked

    /** Uygulama arka plana alindiginda cagrilir. */
    fun lock() {
        locked = true
    }

    /** Dogru PIN girildiginde cagrilir. */
    fun unlock() {
        locked = false
    }

    /**
     * Yeni PIN belirler.
     * @return gecerli bir PIN degilse false.
     */
    fun setPin(context: Context, pin: String): Boolean {
        if (!isValidPin(pin)) return false
        val salt = randomSalt()
        prefs(context).edit()
            .putString(KEY_SALT, salt)
            .putString(KEY_HASH, hash(pin, salt))
            .apply()
        locked = false
        return true
    }

    /** Kilidi tamamen kaldirir. */
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_HASH).remove(KEY_SALT).apply()
        locked = false
    }

    /** Girilen PIN dogru mu? Dogruysa kilidi acar. */
    fun verify(context: Context, pin: String): Boolean {
        val stored = prefs(context).getString(KEY_HASH, null) ?: return false
        val salt = prefs(context).getString(KEY_SALT, null) ?: return false
        val ok = constantTimeEquals(stored, hash(pin, salt))
        if (ok) unlock()
        return ok
    }

    /** PIN kurallari: yalnizca rakam, 4-8 hane. */
    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

    private fun randomSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$salt:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * Karsilastirmayi uzunluga ve icerige gore erken kesmeden yapar;
     * boylece gecen sureden PIN hakkinda bilgi sizmaz.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }
}
