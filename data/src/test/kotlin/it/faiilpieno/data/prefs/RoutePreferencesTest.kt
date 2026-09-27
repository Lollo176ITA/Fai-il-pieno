package it.faiilpieno.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import it.faiilpieno.domain.commute.RouteAvoidance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RoutePreferencesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `independent toggles are atomic and survive reopening the store`() = runBlocking {
        val file = temporary.newFolder().resolve("route.preferences_pb")
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        val prefs = PreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        assertEquals(0, prefs.routePreferences.first().mask)
        RouteAvoidance.entries.map { feature -> launch { prefs.setRouteAvoidance(feature, true) } }.forEach { it.join() }
        assertEquals(7, prefs.routePreferences.first().mask)
        prefs.setRouteAvoidance(RouteAvoidance.TOLLWAYS, false)
        assertEquals(5, prefs.routePreferences.first().mask)
        job.cancel()
        job.join()
        val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = PreferencesRepository(PreferenceDataStoreFactory.create(scope = reopenedScope) { file })
            assertEquals(5, reopened.routePreferences.first().mask)
        } finally {
            reopenedScope.cancel()
        }
    }
}
