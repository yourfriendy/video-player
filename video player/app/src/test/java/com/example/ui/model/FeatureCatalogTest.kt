package com.example.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureCatalogTest {

    @Test
    fun everyGroupHasUniqueKeyAndRealContent() {
        val groups = FeatureCatalog.groups
        assertTrue(groups.isNotEmpty())
        assertEquals("Group keys must be unique", groups.size, groups.map { it.key }.toSet().size)
        groups.forEach { g ->
            assertTrue("${g.key} needs a name and tagline", g.name.isNotBlank() && g.tagline.isNotBlank())
            assertTrue("${g.key} needs features", g.items.isNotEmpty())
            g.items.forEach { assertTrue("${g.key}: blank feature text", it.title.isNotBlank() && it.detail.isNotBlank()) }
        }
    }

    @Test
    fun totalMatchesTheItems() {
        assertEquals(FeatureCatalog.groups.sumOf { it.items.size }, FeatureCatalog.totalFeatures)
    }
}
