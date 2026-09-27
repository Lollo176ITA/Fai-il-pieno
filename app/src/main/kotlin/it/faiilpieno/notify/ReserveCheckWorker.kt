package it.faiilpieno.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import it.faiilpieno.data.prefs.PreferencesRepository
import it.faiilpieno.data.repository.CommuteRepository
import it.faiilpieno.data.repository.PriceRepository
import it.faiilpieno.data.repository.TankRepository
import it.faiilpieno.domain.commute.Commute
import it.faiilpieno.domain.tank.ReserveAlert
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Controllo serale della riserva. Non usa la posizione: solo rifornimenti registrati, tragitti
 * salvati e prezzi già scaricati. Al massimo una notifica al giorno.
 */
@HiltWorker
class ReserveCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val tank: TankRepository,
    private val commutes: CommuteRepository,
    private val prices: PriceRepository,
    private val prefs: PreferencesRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val notifier = ReserveNotifier(applicationContext)
        if (prefs.reserveAlerts.first() != true || !notifier.canNotify()) return Result.success()
        val today = LocalDate.now()
        if (prefs.lastReserveAlert.first() == today) return Result.success()

        // Di sera i tragitti di oggi sono già fatti: si guarda il serbatoio di domani mattina.
        val tomorrow = today.plusDays(1)
        val estimate = tank.estimateOn(tomorrow) ?: return Result.success()
        if (!ReserveAlert.shouldAlert(estimate, today)) return Result.success()
        val reserveDate = estimate.reserveDate ?: return Result.success()

        val list = commutes.commutes.first()
        val stop = ReserveAlert.stopDay(list, tomorrow, maxOf(reserveDate, tomorrow))?.let { bestStop(it, list) }
        notifier.show(today, reserveDate, stop)
        prefs.setLastReserveAlert(today)
        return Result.success()
    }

    /** Il distributore più conveniente tra i tragitti di [day], se c'è un risparmio. */
    private suspend fun bestStop(day: LocalDate, list: List<Commute>): ReserveStop? {
        if (prices.datasetInfo.first() == null) return null
        val car = prefs.carProfile.first()
        val buffer = prefs.routeBufferMeters.first().toDouble()
        val brands = prefs.searchPreferences.first().brands
        return list
            .filter { it.route != null && day.dayOfWeek in it.days }
            .mapNotNull { commutes.advise(it, car, buffer, brands)?.recommended }
            .maxByOrNull { it.netSavingEur }
            ?.let { ReserveStop(day, it) }
    }

    companion object {
        private const val WORK_NAME = "reserve-check"
        private val CHECK_TIME: LocalTime = LocalTime.of(18, 30)

        fun schedule(context: Context) {
            val now = ZonedDateTime.now()
            var next = now.with(CHECK_TIME)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReserveCheckWorker>(Duration.ofHours(24))
                .setInitialDelay(Duration.between(now, next))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
