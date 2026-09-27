package it.faiilpieno.data.ors

import it.faiilpieno.domain.commute.RoutePreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionsOptionsTest {
    @Test fun `avoid features use the ORS wire names and preserve every selection`() {
        for (mask in 0..7) {
            val preferences = RoutePreferences.fromMask(mask)
            val encoded = Json.encodeToString(OrsClient.DirectionsOptions.serializer(), OrsClient.DirectionsOptions(preferences.apiFeatures))
            val features = Json.parseToJsonElement(encoded).jsonObject.getValue("avoid_features").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(preferences.apiFeatures, features)
        }
    }
}
