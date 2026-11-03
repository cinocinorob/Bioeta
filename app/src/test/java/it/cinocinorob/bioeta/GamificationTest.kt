package it.cinocinorob.bioeta

import it.cinocinorob.bioeta.science.BadgeContext
import it.cinocinorob.bioeta.science.Badges
import it.cinocinorob.bioeta.science.DayKind
import it.cinocinorob.bioeta.science.FrozenWeek
import it.cinocinorob.bioeta.science.Game
import it.cinocinorob.bioeta.science.GameInput
import it.cinocinorob.bioeta.science.GameLevel
import it.cinocinorob.bioeta.science.GamePath
import it.cinocinorob.bioeta.science.GamePause
import it.cinocinorob.bioeta.science.PauseReason
import it.cinocinorob.bioeta.science.DailyAdvice
import it.cinocinorob.bioeta.science.Effort
import it.cinocinorob.bioeta.science.Explain
import it.cinocinorob.bioeta.science.AssocResult
import it.cinocinorob.bioeta.science.Exposure
import it.cinocinorob.bioeta.science.OutcomeKind
import it.cinocinorob.bioeta.science.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GamificationTest {
    // lunedì 5 ottobre 2026
    private val mon = LocalDate.of(2026, 10, 5)

    private fun input(steps: Map<LocalDate, Int>, today: LocalDate, readiness: Map<LocalDate, Int> = emptyMap(), active: Map<LocalDate, Int> = emptyMap(), rest: Boolean = false, frozen: Map<LocalDate, FrozenWeek> = emptyMap()) =
        GameInput(today, steps, active, readiness, ageGoal = 9000, restToday = rest, frozen = frozen)

    @Test fun quantileMatchesNumpy() {
        // numpy.quantile([1000, 2000, 3000, 4000, 5000], 0.6) = 3400
        assertEquals(3400.0, Game.quantile(listOf(5000, 1000, 3000, 2000, 4000), 0.6), 1e-9)
        assertEquals(2600.0, Game.quantile(listOf(5000, 1000, 3000, 2000, 4000), 0.4), 1e-9)
    }

    @Test fun calibratingGoalWithFewDays() {
        val steps = (0L..2L).associate { mon.plusDays(it) to 5000 }
        val g = Game.goalFor(mon.plusDays(3), input(steps, mon.plusDays(3)))
        assertTrue(g.calibrating)
        assertEquals(7000, g.target)
    }

    @Test fun adaptiveGoalIsSixtiethPercentileClamped() {
        // 10 giorni: 5000, 5500, …, 9500 → P60 = 7700 → arrotondato a 7750
        val today = mon.plusDays(10)
        val steps = (0L..9L).associate { mon.plusDays(it) to 5000 + 500 * it.toInt() }
        assertEquals(7750, Game.goalFor(today, input(steps, today)).target)
        // passi molto alti: il tetto è l'obiettivo per età
        val high = (0L..9L).associate { mon.plusDays(it) to 15000 }
        assertEquals(9000, Game.goalFor(today, input(high, today)).target)
        // passi molto bassi: il minimo è 3.000
        val low = (0L..9L).associate { mon.plusDays(it) to 1000 }
        assertEquals(3000, Game.goalFor(today, input(low, today)).target)
    }

    @Test fun lowRecoveryMakesLightDayAndMissingItCostsNothing() {
        val d = mon.plusDays(10)
        val steps = (0L..9L).associate { mon.plusDays(it) to 5000 + 500 * it.toInt() } + (d to 3000)
        val i = input(steps, d.plusDays(1), readiness = mapOf(d to 40))
        val o = Game.outcome(d, i)
        assertTrue(o.goal.light)
        assertTrue(o.goal.target!! < 7750)
        assertEquals(DayKind.REST, o.kind)
    }

    @Test fun activeMinutesCountWhenStepsAreFew() {
        val d = mon.plusDays(10)
        val steps = (0L..9L).associate { mon.plusDays(it) to 8000 } + (d to 2500)
        val o = Game.outcome(d, input(steps, d.plusDays(1), active = mapOf(d to 45)))
        assertEquals(DayKind.MET, o.kind)
        val o2 = Game.outcome(d, input(steps, d.plusDays(1), active = mapOf(d to 20)))
        assertEquals(DayKind.MISSED, o2.kind)
    }

    @Test fun restTodayFromRadar() {
        val steps = (0L..9L).associate { mon.plusDays(it) to 8000 }
        val today = mon.plusDays(10)
        val s = Game.evaluate(input(steps + (today to 500), today, rest = true))!!
        assertEquals(DayKind.REST, s.today.kind)
    }

    @Test fun pointsAndLevels() {
        // settimane 1–3 perfette (8.000 passi costanti), settimana 4 con 4 giorni mancati, oggi lunedì della 5ª
        val steps = HashMap<LocalDate, Int>()
        for (k in 0L until 21L) steps[mon.plusDays(k)] = 8000
        for (k in 21L until 28L) steps[mon.plusDays(k)] = if (k < 25) 2000 else 8000
        val today = mon.plusDays(28)
        steps[today] = 1000
        val s = Game.evaluate(input(steps, today))!!
        val w = s.weeks
        assertEquals(70, w[0].points); assertEquals(GameLevel.BRONZO, w[0].levelAfter)
        assertEquals(GameLevel.ARGENTO, w[1].levelAfter)
        assertEquals(GameLevel.ORO, w[2].levelAfter)
        assertEquals(30, w[3].points); assertEquals(GameLevel.ARGENTO, w[3].levelAfter)
        assertEquals(GameLevel.ARGENTO, s.level)
        assertEquals(DayKind.PENDING, s.today.kind)
        assertEquals(70, s.week.points)
        assertEquals(0, s.weekStreak)
        // le settimane finite da più di 2 giorni vanno congelate
        assertEquals(listOf(mon, mon.plusWeeks(1), mon.plusWeeks(2)), s.toFreeze.map { it.start })
    }

    @Test fun frozenWeeksAreNotRecomputed() {
        val steps = (0L until 14L).associate { mon.plusDays(it) to 8000 }
        val today = mon.plusDays(14)
        // la settimana 1 era stata chiusa con 30 punti e livello Blu: resta così anche se i dati ora dicono altro
        val frozen = mapOf(mon to FrozenWeek(mon, 30, 3, 4, true, GameLevel.BLU))
        val s = Game.evaluate(input(steps, today, frozen = frozen))!!
        assertEquals(30, s.weeks[0].points)
        assertEquals(GameLevel.BRONZO, s.weeks[1].levelAfter)
        assertFalse(s.toFreeze.any { it.start == mon })
    }

    @Test fun gentleStreakUsesOneJollyPerWeek() {
        val steps = HashMap<LocalDate, Int>()
        for (k in 0L until 14L) steps[mon.plusDays(k)] = 8000
        steps[mon.plusDays(9)] = 1000   // mercoledì della 2ª settimana: jolly
        val today = mon.plusDays(14)
        val s = Game.evaluate(input(steps, today))!!
        assertEquals(13, s.dayStreak)
        // un secondo giorno mancato nella stessa settimana interrompe la serie
        steps[mon.plusDays(11)] = 1000
        val s2 = Game.evaluate(input(steps, today))!!
        // a ritroso: 2 giorni, jolly, 1 giorno, secondo mancato → 3
        assertEquals(3, s2.dayStreak)
        // un giorno senza orologio non interrompe e non conta
        steps.remove(mon.plusDays(11)); steps[mon.plusDays(11)] = 0
        val s3 = Game.evaluate(input(steps, today))!!
        assertEquals(12, s3.dayStreak)
    }

    @Test fun badgesRewardThresholdsNotExcess() {
        val steps = (0L until 7L).associate { mon.plusDays(it) to 8000 }
        val g = Game.evaluate(input(steps, mon.plusDays(7)))
        assertNotNull(g)
        val ctx = BadgeContext(g, mapOf(mon to 160), 7200.0, 82.0, 7, true, 1, true, 0, 0)
        val got = Badges.satisfied(ctx)
        assertTrue(listOf("first_goal", "full_week", "who_150", "steps_7k", "sleep_regular", "journal_7", "bp_protocol", "first_lab", "bio_age").all { it in got })
        assertFalse("who_300" in got)
        assertFalse("ecg_first" in got)
        // ogni traguardo ha un id unico
        assertEquals(Badges.all.size, Badges.all.map { it.id }.toSet().size)
    }

    @Test fun pausedDaysDoNotCostPointsOrBreakStreak() {
        val steps = HashMap<LocalDate, Int>()
        for (k in 0L until 14L) steps[mon.plusDays(k)] = 8000
        for (k in 7L..10L) steps[mon.plusDays(k)] = 500   // influenza da martedì a venerdì della 2ª settimana
        val today = mon.plusDays(14)
        val pause = GamePause(PauseReason.ILLNESS, mon.plusDays(7), mon.plusDays(10))
        val i = GameInput(today, steps, emptyMap(), emptyMap(), 9000, paused = pause.days(today))
        val s = Game.evaluate(i)!!
        assertEquals(70, s.weeks[1].points)
        assertTrue(s.weeks[1].days.filter { it.date in pause.days(today) }.all { it.kind == DayKind.REST })
        assertEquals(10, s.dayStreak)
        // senza pausa gli stessi giorni costano 40 punti
        assertEquals(30, Game.evaluate(i.copy(paused = emptySet()))!!.weeks[1].points)
    }

    @Test fun openPauseIsCappedAt30Days() {
        val p = GamePause(PauseReason.TRAVEL, mon, null)
        assertEquals(30, p.days(mon.plusDays(60)).size)
        assertFalse(p.active(mon.plusDays(31)))
        assertTrue(p.active(mon.plusDays(5)))
    }

    @Test fun dailyAdvice() {
        assertEquals(Effort.REST, DailyAdvice.effort(35, 10, false, false, false))
        assertEquals(Effort.EASY, DailyAdvice.effort(52, 40, false, false, false))
        assertEquals(Effort.EASY, DailyAdvice.effort(80, 60, true, false, false))
        assertEquals(Effort.HARD, DailyAdvice.effort(82, 80, false, false, false))
        assertEquals(Effort.MODERATE, DailyAdvice.effort(70, 50, false, false, false))
        assertEquals(Effort.REST, DailyAdvice.effort(90, 90, false, true, false))
        assertEquals(Effort.REST, DailyAdvice.effort(90, 90, false, false, true))
    }

    @Test fun activityPathHasBandAndGoal() {
        val steps = (0L until 20L).associate { mon.plusDays(it) to 5000 + 250 * (it % 8).toInt() }
        val today = mon.plusDays(19)
        val path = GamePath.path(GameInput(today, steps, emptyMap(), emptyMap(), 9000), 14)
        assertEquals(14, path.size)
        assertEquals(today, path.last().date)
        assertTrue(path.all { d -> d.low != null && d.goal != null && d.low!! <= d.goal!! })
    }

    @Test fun explanationIsPlainAndDescriptive() {
        val e = Exposure("tag:alcohol", "Alcol", emptySet(), emptySet(), auto = false)
        val r = AssocResult(e, OutcomeKind.HRV, -14.0, -22.0, -6.0, 0.002, 0.02, 9, 31, Verdict.CONFIRMED, meanWith = 42.0, meanWithout = 49.0)
        val t = Explain.finding(r)
        assertEquals("−14%", t.big)
        assertTrue(t.sentence.contains("più bassa del 14%"))
        assertTrue(t.sentence.contains("42 ms contro 49 ms"))
        assertFalse(t.sentence.contains("abbassa"))
        assertEquals("alcohol", t.experimentKey)
        assertTrue(t.experimentAvoid)
        assertTrue(t.reliability.startsWith("Affidabile"))
    }
}
