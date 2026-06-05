package com.cuuper.sfpark.domain

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchAnchorTest {
    @Test
    fun neighborhoodAnchorsStayInsideSanFrancisco() {
        SearchAnchor.neighborhoods.forEach { anchor ->
            assertTrue("${anchor.label} latitude", anchor.center.latitude in 37.68..37.84)
            assertTrue("${anchor.label} longitude", anchor.center.longitude in -122.54..-122.34)
        }
    }

    @Test
    fun resolvesSavedAnchorIdsWithSafeFallback() {
        assertEquals("Mission", SearchAnchor.byId("mission").label)
        assertEquals(SearchAnchor.sfCenter, SearchAnchor.byId("missing"))
        assertEquals(SearchAnchor.sfCenter, SearchAnchor.byId(null))
    }
}
