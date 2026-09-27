package it.faiilpieno.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import it.faiilpieno.domain.brand.BrandGroup
import it.faiilpieno.domain.model.CarProfile
import it.faiilpieno.domain.model.ConsumptionUnit
import it.faiilpieno.domain.model.FuelCategory
import it.faiilpieno.domain.model.ServiceMode
import it.faiilpieno.domain.nearby.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class SearchPreferences(
    val sortMode: SortMode = SortMode.PRICE,
    val brands: Set<BrandGroup> = emptySet(),
)

@Singleton
class PreferencesRepository @Inject constructor(private val store: DataStore<Preferences>) {

    val carProfile: Flow<CarProfile> = store.data.map { p ->
        val defaults = CarProfile()
        CarProfile(
            fuel = p[FUEL].toEnum(defaults.fuel),
            tankCapacity = p[TANK] ?: defaults.tankCapacity,
            consumptionPer100Km = p[CONSUMPTION] ?: defaults.consumptionPer100Km,
            consumptionUnit = p[CONSUMPTION_UNIT].toEnum(defaults.consumptionUnit),
            serviceMode = p[SERVICE_MODE].toEnum(defaults.serviceMode),
            isConfigured = p[CAR_CONFIGURED] ?: false,
        )
    }.distinctUntilChanged()

    val searchPreferences: Flow<SearchPreferences> = store.data.map { p ->
        SearchPreferences(
            sortMode = p[SORT_MODE].toEnum(SortMode.PRICE),
            brands = p[BRANDS].orEmpty().mapNotNull { name -> BrandGroup.entries.firstOrNull { it.name == name } }.toSet(),
        )
    }.distinctUntilChanged()

    /** Distanza massima dal percorso abituale entro cui cercare i distributori. */
    val routeBufferMeters: Flow<Int> = store.data.map { it[ROUTE_BUFFER] ?: DEFAULT_ROUTE_BUFFER_M }.distinctUntilChanged()

    val onboardingDone: Flow<Boolean> = store.data.map { it[ONBOARDING_DONE] ?: false }.distinctUntilChanged()

    suspend fun saveCarProfile(profile: CarProfile) {
        store.edit {
            it[FUEL] = profile.fuel.name
            it[TANK] = profile.tankCapacity
            it[CONSUMPTION] = profile.consumptionPer100Km
            it[CONSUMPTION_UNIT] = profile.consumptionUnit.name
            it[SERVICE_MODE] = profile.serviceMode.name
            it[CAR_CONFIGURED] = true
        }
    }

    suspend fun setFuel(fuel: FuelCategory) {
        store.edit { it[FUEL] = fuel.name }
    }

    suspend fun setServiceMode(mode: ServiceMode) {
        store.edit { it[SERVICE_MODE] = mode.name }
    }

    suspend fun setSortMode(mode: SortMode) {
        store.edit { it[SORT_MODE] = mode.name }
    }

    suspend fun setBrands(brands: Set<BrandGroup>) {
        store.edit { it[BRANDS] = brands.map(BrandGroup::name).toSet() }
    }

    suspend fun setRouteBuffer(meters: Int) {
        store.edit { it[ROUTE_BUFFER] = meters }
    }

    suspend fun setOnboardingDone() {
        store.edit { it[ONBOARDING_DONE] = true }
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    companion object {
        const val DEFAULT_ROUTE_BUFFER_M = 500
        val ROUTE_BUFFER_OPTIONS = listOf(250, 500, 1_000, 2_000)

        private val FUEL = stringPreferencesKey("car_fuel")
        private val TANK = doublePreferencesKey("car_tank")
        private val CONSUMPTION = doublePreferencesKey("car_consumption_per_100km")
        private val CONSUMPTION_UNIT = stringPreferencesKey("car_consumption_unit")
        private val SERVICE_MODE = stringPreferencesKey("service_mode")
        private val CAR_CONFIGURED = booleanPreferencesKey("car_configured")
        private val SORT_MODE = stringPreferencesKey("sort_mode")
        private val BRANDS = stringSetPreferencesKey("brand_filter")
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val ROUTE_BUFFER = intPreferencesKey("route_buffer_m")
    }
}
