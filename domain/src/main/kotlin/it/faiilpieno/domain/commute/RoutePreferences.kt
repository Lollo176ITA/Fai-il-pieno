package it.faiilpieno.domain.commute

enum class RouteAvoidance(val bit: Int, val apiValue: String) {
    HIGHWAYS(1, "highways"), TOLLWAYS(2, "tollways"), FERRIES(4, "ferries"),
}

data class RoutePreferences(val avoid: Set<RouteAvoidance> = emptySet()) {
    val mask: Int get() = avoid.fold(0) { mask, feature -> mask or feature.bit }
    val apiFeatures: List<String> get() = RouteAvoidance.entries.filter { it in avoid }.map { it.apiValue }

    companion object {
        fun fromMask(mask: Int) = RoutePreferences(RouteAvoidance.entries.filterTo(mutableSetOf()) { mask and it.bit != 0 })
    }
}
