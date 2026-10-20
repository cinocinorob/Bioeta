package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.JKind
import it.cinocinorob.bioeta.science.JournalCatalog
import it.cinocinorob.bioeta.science.JournalEntry
import it.cinocinorob.bioeta.science.JournalTags
import it.cinocinorob.bioeta.science.answer
import it.cinocinorob.bioeta.science.hasExplicitAnswers
import it.cinocinorob.bioeta.science.withAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JournalTest {
    private val d = LocalDate.of(2026, 10, 20)

    @Test fun answersAreExplicitAndReversible() {
        var e = JournalEntry(d)
        assertNull(e.answer("Alcol"))
        e = e.withAnswer("Alcol", true, 2.0)
        assertEquals(true, e.answer("Alcol")); assertEquals(2.0, e.amounts["Alcol"]!!, 0.0)
        e = e.withAnswer("Alcol", false)
        assertEquals(false, e.answer("Alcol")); assertNull(e.amounts["Alcol"])
        e = e.withAnswer("Alcol", null)
        assertNull(e.answer("Alcol"))
        assertTrue(e.hasExplicitAnswers)
    }

    @Test fun legacyTagsAreCatalogKeys() {
        // Le etichette delle versioni precedenti devono restare valide come chiavi
        JournalTags.ALL.forEach { t -> assertTrue("manca $t", JournalCatalog.byKey(t) != null) }
    }

    @Test fun catalogKeysAreUniqueAndCountsHaveUnits() {
        val keys = JournalCatalog.ITEMS.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        JournalCatalog.ITEMS.filter { it.kind == JKind.COUNT }.forEach { assertTrue(it.unit != null) }
        assertTrue(JournalCatalog.DEFAULT_KEYS.size in 6..14)
    }

    @Test fun customItemsResolve() {
        val i = JournalCatalog.resolve("custom:Ginnastica posturale")
        assertEquals("Ginnastica posturale", i.label)
    }
}
