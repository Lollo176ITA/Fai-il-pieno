package it.faiilpieno.data.work

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    /** In coda, ma senza rete: partirà appena torna la connessione. */
    data object WaitingForNetwork : SyncStatus
    data class Failed(val reason: Reason) : SyncStatus

    enum class Reason { NETWORK, FORMAT, UNKNOWN }
}

@Singleton
class DataSync @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val workManager get() = WorkManager.getInstance(context)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Download giornaliero, dopo la pubblicazione del file MIMIT (verso le 9). */
    fun scheduleDaily() {
        val request = PeriodicWorkRequestBuilder<DailyDataWorker>(Duration.ofHours(24))
            .setConstraints(networkConstraint)
            .setInitialDelay(delayUntil(DAILY_TIME))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(30))
            .build()
        workManager.enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Aggiornamento immediato (primo avvio, dati vecchi o richiesta dell'utente). */
    fun refreshNow(force: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<DailyDataWorker>()
            .setConstraints(networkConstraint)
            .setInputData(workDataOf(DailyDataWorker.KEY_FORCE to force))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
            .build()
        workManager.enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.KEEP, request)
    }

    val status: Flow<SyncStatus> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(NOW_WORK),
        workManager.getWorkInfosForUniqueWorkFlow(DAILY_WORK),
    ) { now, daily ->
        val infos = now + daily
        when {
            infos.any { it.state == WorkInfo.State.RUNNING } -> SyncStatus.Running
            now.any { it.state == WorkInfo.State.ENQUEUED } ->
                if (isOnline()) SyncStatus.Running else SyncStatus.WaitingForNetwork
            else -> now.lastOrNull { it.state == WorkInfo.State.FAILED }?.let { failed ->
                SyncStatus.Failed(
                    when (failed.outputData.getString(DailyDataWorker.KEY_ERROR)) {
                        DailyDataWorker.ERROR_NETWORK -> SyncStatus.Reason.NETWORK
                        DailyDataWorker.ERROR_FORMAT -> SyncStatus.Reason.FORMAT
                        else -> SyncStatus.Reason.UNKNOWN
                    },
                )
            } ?: SyncStatus.Idle
        }
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun delayUntil(time: LocalTime): Duration {
        val now = ZonedDateTime.now()
        var next = now.with(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    private companion object {
        const val DAILY_WORK = "mimit-daily"
        const val NOW_WORK = "mimit-now"
        val DAILY_TIME: LocalTime = LocalTime.of(10, 0)
    }
}
