package com.nabil.usdtwallet.domain.usecase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.nabil.usdtwallet.data.repository.SecureStorage
import com.nabil.usdtwallet.data.repository.WalletRepository
import com.nabil.usdtwallet.data.repository.Result
import java.util.concurrent.TimeUnit

class WalletNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val CHANNEL_ID = "wallet_notifications"
        private const val WORK_NAME = "wallet_check"
        private const val KEY_LAST_TX = "last_tx_id"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WalletNotificationWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val storage = SecureStorage(context)
        val address = storage.getAddress() ?: return Result.success()

        val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        val lastTxId = prefs.getString(KEY_LAST_TX, "") ?: ""

        return try {
            val repo = WalletRepository()
            val result = repo.getTransactions(address)
            if (result is com.nabil.usdtwallet.data.repository.Result.Success) {
                val txList = result.data
                val latestIncoming = txList.firstOrNull { it.isIncoming }

                if (latestIncoming != null && latestIncoming.txId != lastTxId) {
                    showNotification(
                        title = "💰 استقبلت USDT",
                        body = "وصل ${String.format("%.2f", latestIncoming.amount)} USDT لمحفظتك"
                    )
                    prefs.edit().putString(KEY_LAST_TX, latestIncoming.txId).apply()
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(title: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "إشعارات المحفظة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "إشعارات المعاملات الواردة" }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
