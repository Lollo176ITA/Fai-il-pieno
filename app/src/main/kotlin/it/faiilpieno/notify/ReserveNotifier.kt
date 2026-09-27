package it.faiilpieno.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.faiilpieno.MainActivity
import it.faiilpieno.R
import it.faiilpieno.domain.commute.RankedRouteOffer
import it.faiilpieno.ui.format.Fmt
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Dove conviene fermarsi prima della riserva: il giorno del tragitto e il distributore consigliato. */
data class ReserveStop(val day: LocalDate, val offer: RankedRouteOffer)

/** Notifica "Tra 2 giorni sei in riserva. Domani passi davanti a …". */
class ReserveNotifier(private val context: Context) {

    fun canNotify(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // Controllato da canNotify().
    fun show(today: LocalDate, reserveDate: LocalDate, stop: ReserveStop?) {
        if (!canNotify()) return
        createChannel()
        val headline = headline(today, reserveDate)
        val detail = stop?.let { stopText(today, it) } ?: context.getString(R.string.notif_no_stop)
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gas_station)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$headline $detail"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun headline(today: LocalDate, reserveDate: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, reserveDate).toInt()
        return when {
            days <= 0 -> context.getString(R.string.tank_in_reserve)
            days == 1 -> context.getString(R.string.today_reserve_tomorrow)
            else -> context.resources.getQuantityString(R.plurals.today_reserve_days, days, days)
        }
    }

    /** "Domani passi davanti a Eni a 2,089 €/l: risparmi 2,51 €." */
    private fun stopText(today: LocalDate, stop: ReserveStop): String {
        val day = if (stop.day == today.plusDays(1)) {
            context.getString(R.string.when_tomorrow)
        } else {
            stop.day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALY).replaceFirstChar { it.titlecase(Locale.ITALY) }
        }
        val price = stop.offer.offer.price
        val unit = context.getString(if (price.category.isSoldByKg) R.string.unit_per_kg else R.string.unit_per_liter)
        return context.getString(
            R.string.notif_stop,
            day,
            stop.offer.offer.station.brand,
            context.getString(R.string.format_price, Fmt.priceNumber(price.priceMilli), unit),
            context.getString(R.string.format_euro, Fmt.euros(stop.offer.netSavingEur)),
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.notif_channel_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "reserve"
        const val NOTIFICATION_ID = 1
    }
}
