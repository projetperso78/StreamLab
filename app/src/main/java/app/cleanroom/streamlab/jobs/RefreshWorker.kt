package app.cleanroom.streamlab.jobs

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.cleanroom.streamlab.StreamLabApplication
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong("profileId", -1)
        if (id < 0) return Result.failure()
        return try {
            val repository = (applicationContext as StreamLabApplication).repository
            if (inputData.getBoolean("epg", false)) repository.refreshGuide(id) else repository.refreshProfile(id)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}

object RefreshScheduler {
    fun once(context: Context, profileId: Long, epg: Boolean) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInputData(Data.Builder().putLong("profileId", profileId).putBoolean("epg", epg).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("refresh-$profileId-$epg", ExistingWorkPolicy.REPLACE, request)
    }
    fun periodic(context: Context, profileId: Long, epg: Boolean, hours: Int) {
        val manager = WorkManager.getInstance(context)
        val name = "periodic-$profileId-$epg"
        if (hours <= 0) { manager.cancelUniqueWork(name); return }
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(hours.coerceAtLeast(15).toLong(), TimeUnit.HOURS)
            .setInputData(Data.Builder().putLong("profileId", profileId).putBoolean("epg", epg).build()).build()
        manager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
