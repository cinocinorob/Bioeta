package it.cinocinorob.bioeta.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Finestre di lettura da Connessione Salute allineate alla mezzanotte locale.
 *
 * Prima la finestra partiva da «adesso − N giorni»: il giorno più vecchio veniva letto solo in parte, e quale
 * parte dipendeva dall'ora in cui si apriva l'app. La lettura veloce sovrascriveva poi i valori completi con
 * quelli troncati (allenamenti del mattino spariti, notte accorciata) finché la lettura completa non li
 * ripristinava: per questo gli stessi giorni mostravano numeri diversi a ogni apertura.
 */
object ReadWindow {
    /** Limite di Connessione Salute senza il permesso per i dati storici. */
    val NO_HISTORY_LIMIT: Duration = Duration.ofDays(30)

    fun start(days: Long, now: Instant, zone: ZoneId, history: Boolean): Instant {
        val aligned = now.atZone(zone).toLocalDate().minusDays(days).atStartOfDay(zone).toInstant()
        return if (history) aligned else maxOf(aligned, now.minus(NO_HISTORY_LIMIT))
    }

    /**
     * Primo giorno di cui una lettura che parte da [start] contiene dati completi.
     * - Valori puntuali (FC a riposo, HRV, peso…): il giorno di [start] se la finestra parte a mezzanotte.
     * - Sonno: sempre il giorno dopo, perché la notte attribuita al giorno di [start] è iniziata la sera prima.
     */
    fun firstCompleteDay(start: Instant, zone: ZoneId, overnight: Boolean): LocalDate {
        val d = start.atZone(zone).toLocalDate()
        val atMidnight = d.atStartOfDay(zone).toInstant() == start
        return if (atMidnight && !overnight) d else d.plusDays(1)
    }
}
