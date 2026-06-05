package com.cuuper.sfpark.domain

data class SearchAnchor(
    val id: String,
    val label: String,
    val center: LatLng
) {
    companion object {
        val sfCenter = SearchAnchor("sf-center", "SF center", LatLng(37.7749, -122.4194))
        val neighborhoods = listOf(
            SearchAnchor("mission", "Mission", LatLng(37.7599, -122.4148)),
            SearchAnchor("soma", "SoMa", LatLng(37.7793, -122.3990)),
            SearchAnchor("hayes", "Hayes", LatLng(37.7767, -122.4241)),
            SearchAnchor("marina", "Marina", LatLng(37.8037, -122.4368)),
            SearchAnchor("sunset", "Sunset", LatLng(37.7539, -122.4869)),
            SearchAnchor("richmond", "Richmond", LatLng(37.7806, -122.4644))
        )

        fun byId(id: String?): SearchAnchor {
            return (listOf(sfCenter) + neighborhoods).firstOrNull { it.id == id } ?: sfCenter
        }
    }
}
