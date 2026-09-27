package it.faiilpieno.domain.commute

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutePreferencesTest {
    @Test fun `all combinations survive persistence`() {
        for (mask in 0..7) assertEquals(mask, RoutePreferences.fromMask(mask).mask)
    }

    @Test fun `highways and tollways remain independent restrictions`() {
        assertEquals(listOf("highways"), RoutePreferences.fromMask(1).apiFeatures)
        assertEquals(listOf("tollways"), RoutePreferences.fromMask(2).apiFeatures)
        assertEquals(listOf("highways", "tollways", "ferries"), RoutePreferences.fromMask(7).apiFeatures)
        assertEquals(emptyList<String>(), RoutePreferences().apiFeatures)
    }

    @Test fun `unknown bits are ignored for forward compatibility`() {
        assertEquals(RoutePreferences.fromMask(5), RoutePreferences.fromMask(13))
    }
}
