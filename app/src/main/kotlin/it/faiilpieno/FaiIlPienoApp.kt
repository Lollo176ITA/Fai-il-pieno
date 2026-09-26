package it.faiilpieno

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import it.faiilpieno.data.work.DataSync
import org.maplibre.android.MapLibre
import javax.inject.Inject

@HiltAndroidApp
class FaiIlPienoApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var dataSync: DataSync

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        dataSync.scheduleDaily()
    }
}
