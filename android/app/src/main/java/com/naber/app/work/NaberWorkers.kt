package com.naber.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.naber.app.Naber
import com.naber.app.data.LocalFiles
import com.naber.app.data.LocalStore
import com.naber.app.data.Message
import java.util.concurrent.TimeUnit

/**
 * Arka plan isleri.
 *
 * Iki is var: eski dosyalarin temizligi ve gonderilememis mesajlarin
 * yeniden denenmesi. Ikisi de uygulama acikken yapilabilirdi ama o zaman
 * ya acilisi yavaslatir ya da kullanici uygulamayi acana kadar hic
 * yapilmazdi. WorkManager bunlari uygun bir ana birakir ve is yarida
 * kalirsa kendisi tekrar dener.
 */
object NaberWork {

    private const val MAINTENANCE = "naber-bakim"
    private const val OUTBOX = "naber-kuyruk"

    /** Uygulama her acildiginda cagrilir; isler zaten kayitliysa tekrarlanmaz. */
    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context)

        manager.enqueueUniquePeriodicWork(
            MAINTENANCE,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    // Temizlik aceleye getirilmez: pil azken disk taramak
                    // degecek bir is degil.
                    Constraints.Builder().setRequiresBatteryNotLow(true).build()
                )
                .build()
        )

        flushOutbox(context)
    }

    /** Gonderilemeyen mesajlar icin yeniden deneme isi kurar. */
    fun flushOutbox(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            OUTBOX,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<OutboxWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
        )
    }
}

/** Eski yerel kopyalari siler. */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        LocalFiles.cleanup(applicationContext)
        return Result.success()
    }
}

/**
 * Gonderilememis mesajlari yeniden dener.
 *
 * Yalnizca metin mesajlari denenir: gorsel ve ses icin yerel dosya
 * silinmis olabilir, yarim bir yukleme baslatmak dogru olmaz.
 *
 * Ayni mesajin iki kez gitmesinden korkulmaz: sunucu client_id'ye bakip
 * ayni mesaji ikinci kez olusturmuyor.
 */
class OutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Naber.init(applicationContext)
        if (!Naber.session.isLoggedIn) return Result.success()

        var failed = false

        for (conversationId in LocalStore.conversationIds(applicationContext)) {
            val stored = LocalStore.loadMessages(applicationContext, conversationId)
            val pending = stored.filter {
                it.id <= 0 && it.clientId.isNotBlank() && it.type == "text" && it.body.isNotBlank()
            }
            if (pending.isEmpty()) continue

            val sent = HashMap<String, Message>()
            for (message in pending) {
                runCatching { Naber.api.sendText(conversationId, message.body, message.clientId) }
                    .onSuccess { sent[message.clientId] = it }
                    .onFailure { failed = true }
            }

            if (sent.isNotEmpty()) {
                LocalStore.saveMessages(
                    applicationContext,
                    conversationId,
                    stored.map { sent[it.clientId] ?: it }
                )
            }
        }

        // Basarisiz olanlari WorkManager kendi bekleme suresiyle tekrar
        // dener; burada dongu kurmaya gerek yok.
        return if (failed) Result.retry() else Result.success()
    }
}
