package it.faiilpieno.domain.brand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandNormalizerTest {

    @Test
    fun `varianti dei marchi principali`() {
        mapOf(
            "Agip Eni" to "Eni",
            "AGIP" to "Eni",
            "Api-Ip" to "IP",
            "Total Erg" to "IP",
            "Q8" to "Q8",
            "Esso" to "Esso",
            "Tamoil" to "Tamoil",
            "Shell" to "Shell",
            "Pompe Bianche" to "Pompe bianche",
            "Carburanti Bianchi" to "Pompe bianche",
            "Bandiera non selezionata" to "Pompe bianche",
            "" to "Pompe bianche",
            "ACI_PT" to "ACI",
            "COOP" to "Enercoop",
            "PITSTOP" to "Pit Stop",
        ).forEach { (raw, expected) -> assertEquals(raw, expected, BrandNormalizer.normalize(raw)) }
    }

    @Test
    fun `maiuscolo diventa iniziali maiuscole, le sigle brevi restano`() {
        assertEquals("Keropetrol", BrandNormalizer.normalize("KEROPETROL"))
        assertEquals("Azzalini Energie", BrandNormalizer.normalize("AZZALINI ENERGIE"))
        assertEquals("ICM", BrandNormalizer.normalize("ICM"))
        assertEquals("Sia fuel", BrandNormalizer.normalize("Sia fuel"))
    }

    @Test
    fun `rimuove spazi e forma societaria`() {
        assertEquals("Powergas", BrandNormalizer.normalize(" POWERGAS S.R.L."))
        assertEquals("AP Petroli", BrandNormalizer.normalize("AP Petroli S.R.L."))
    }

    @Test
    fun `gruppi del filtro`() {
        assertEquals(BrandGroup.ENI, BrandGroup.of(BrandNormalizer.normalize("Agip Eni")))
        assertEquals(BrandGroup.POMPE_BIANCHE, BrandGroup.of("Pompe bianche"))
        assertEquals(BrandGroup.ALTRI, BrandGroup.of("Keropetrol"))
    }

    @Test
    fun `filtro marchi vuoto accetta tutto`() {
        assertTrue(BrandGroup.accepts(emptySet(), "Keropetrol"))
        assertTrue(BrandGroup.accepts(setOf(BrandGroup.ALTRI), "Keropetrol"))
        assertFalse(BrandGroup.accepts(setOf(BrandGroup.ENI), "Keropetrol"))
    }
}
