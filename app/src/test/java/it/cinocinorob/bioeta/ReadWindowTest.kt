package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.data.ReadWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ReadWindowTest {
    private val rome = ZoneId.of("Europe/Rome")

    @Test fun startDoesNotDependOnTimeOfDay() {
        val morning = ZonedDateTime.of(2026, 10, 17, 7, 5, 0, 0, rome).toInstant()
        val night = ZonedDateTime.of(2026, 10, 17, 23, 50, 0, 0, rome).toInstant()
        val a = ReadWindow.start(4, morning, rome, history = true)
        val b = ReadWindow.start(4, night, rome, history = true)
        assertEquals(a, b)
        assertEquals(LocalDate.of(2026, 10, 13).atStartOfDay(rome).toInstant(), a)
    }

    @Test fun firstCompleteDayExcludesTruncatedNight() {
        val start = ReadWindow.start(4, ZonedDateTime.of(2026, 10, 17, 21, 46, 0, 0, rome).toInstant(), rome, true)
        // Valori puntuali: il 13 è letto per intero. Sonno: la notte del 13 è iniziata il 12, quindi dal 14.
        assertEquals(LocalDate.of(2026, 10, 13), ReadWindow.firstCompleteDay(start, rome, overnight = false))
        assertEquals(LocalDate.of(2026, 10, 14), ReadWindow.firstCompleteDay(start, rome, overnight = true))
    }

    @Test fun quickWindowCoversItsOldestDayCompletely() {
        // La lettura veloce sostituisce i giorni da (oggi − 3): quel giorno deve essere completo per sonno e allenamenti
        val now = ZonedDateTime.of(2026, 10, 17, 21, 46, 0, 0, rome).toInstant()
        val from = LocalDate.of(2026, 10, 17).minusDays(3)
        val start = ReadWindow.start(4, now, rome, true)
        assertTrue(!ReadWindow.firstCompleteDay(start, rome, overnight = true).isAfter(from))
        assertTrue(!ReadWindow.firstCompleteDay(start, rome, overnight = false).isAfter(from))
    }

    @Test fun withoutHistoryPermissionTheLimitIsThirtyDaysAndTheFirstDayIsDropped() {
        val now = ZonedDateTime.of(2026, 10, 17, 21, 46, 0, 0, rome).toInstant()
        val start = ReadWindow.start(120, now, rome, history = false)
        assertEquals(now.minus(Duration.ofDays(30)), start)
        assertEquals(LocalDate.of(2026, 9, 18), ReadWindow.firstCompleteDay(start, rome, overnight = false))
    }

    @Test fun dstChangeKeepsMidnight() {
        // 25 ottobre 2026: fine dell'ora legale in Italia
        val now = ZonedDateTime.of(2026, 10, 27, 10, 0, 0, 0, rome).toInstant()
        val start = ReadWindow.start(4, now, rome, true)
        assertEquals(0, start.atZone(rome).hour)
        assertEquals(LocalDate.of(2026, 10, 23), start.atZone(rome).toLocalDate())
    }
}
