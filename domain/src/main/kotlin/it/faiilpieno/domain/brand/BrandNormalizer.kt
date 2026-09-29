package it.faiilpieno.domain.brand

/**
 * Riconduce le varianti di scrittura della colonna Bandiera a un marchio canonico
 * (es. "Agip Eni" → "Eni", "Api-Ip" → "IP", "Carburanti Bianchi" → "Pompe bianche").
 */
object BrandNormalizer {

    const val ENI = "Eni"
    const val IP = "IP"
    const val Q8 = "Q8"
    const val ESSO = "Esso"
    const val TAMOIL = "Tamoil"
    const val SHELL = "Shell"
    const val UNBRANDED = "Pompe bianche"

    /** Chiave: bandiera in minuscolo, senza spazi superflui. */
    private val aliases = mapOf(
        "agip eni" to ENI,
        "agip" to ENI,
        "eni" to ENI,
        "enilive" to ENI,
        "api-ip" to IP,
        "api" to IP,
        "ip" to IP,
        // La rete TotalErg è confluita in IP: alcuni impianti espongono ancora il vecchio nome.
        "total erg" to IP,
        "totalerg" to IP,
        "q8" to Q8,
        "kuwait" to Q8,
        "esso" to ESSO,
        "tamoil" to TAMOIL,
        "shell" to SHELL,
        "pompe bianche" to UNBRANDED,
        "pompa bianca" to UNBRANDED,
        "carburanti bianchi" to UNBRANDED,
        "bandiera non selezionata" to UNBRANDED,
        "gestori.prezzibenzina.it" to UNBRANDED,
        "" to UNBRANDED,
        "aci_cl" to "ACI",
        "aci_pt" to "ACI",
        "coop" to "Enercoop",
        "pitstop" to "Pit Stop",
        "pit stop" to "Pit Stop",
    )

    private val companySuffix = Regex("""\s+(s\.?r\.?l\.?|s\.?p\.?a\.?|s\.?n\.?c\.?)$""", RegexOption.IGNORE_CASE)
    private val spaces = Regex("""\s+""")

    fun normalize(raw: String): String {
        val cleaned = raw.trim().replace(spaces, " ").replace(companySuffix, "")
        aliases[cleaned.lowercase()]?.let { return it }
        return if (isShouting(cleaned)) toTitleCase(cleaned) else cleaned
    }

    /** Scritte tutte in maiuscolo (es. "KEROPETROL"), escluse le sigle brevi come "ICM". */
    private fun isShouting(s: String): Boolean {
        val letters = s.filter { it.isLetter() }
        return letters.length >= 4 && letters.all { it.isUpperCase() }
    }

    private fun toTitleCase(s: String): String =
        s.lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
}

/** Gruppi mostrati nel filtro marchi: i principali più "Altri". */
enum class BrandGroup(val canonical: String?) {
    ENI(BrandNormalizer.ENI),
    IP(BrandNormalizer.IP),
    Q8(BrandNormalizer.Q8),
    ESSO(BrandNormalizer.ESSO),
    TAMOIL(BrandNormalizer.TAMOIL),
    SHELL(BrandNormalizer.SHELL),
    POMPE_BIANCHE(BrandNormalizer.UNBRANDED),
    ALTRI(null);

    companion object {
        private val byCanonical = entries.filter { it.canonical != null }.associateBy { it.canonical }

        fun of(brand: String): BrandGroup = byCanonical[brand] ?: ALTRI

        /** Un filtro vuoto accetta tutti i marchi. */
        fun accepts(filter: Set<BrandGroup>, brand: String): Boolean = filter.isEmpty() || of(brand) in filter

        val namedBrands: List<String> = entries.mapNotNull { it.canonical }
    }
}
