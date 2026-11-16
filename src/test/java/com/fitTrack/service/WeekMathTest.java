package com.fitTrack.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeekMathTest {

    // Wednesday: week 1 is the calendar week containing it.
    private static final LocalDate START = LocalDate.of(2026, 11, 11);

    @Test
    void weekIsAnchoredToTheMondayOfTheStartWeek() {
        assertEquals(1, WeekMath.weekFor(START, LocalDate.of(2026, 11, 11)));
        assertEquals(1, WeekMath.weekFor(START, LocalDate.of(2026, 11, 15))); // Sunday
        assertEquals(2, WeekMath.weekFor(START, LocalDate.of(2026, 11, 16))); // next Monday
        assertEquals(3, WeekMath.weekFor(START, LocalDate.of(2026, 11, 23)));
    }

    @Test
    void weekStartIsAlwaysMonday() {
        assertEquals(LocalDate.of(2026, 11, 9), WeekMath.weekStart(START, 1));
        assertEquals(LocalDate.of(2026, 11, 16), WeekMath.weekStart(START, 2));
    }

    @Test
    void neverBelowWeekOneForFutureStart() {
        assertEquals(1, WeekMath.weekFor(START, START.minusDays(20)));
    }

    @Test
    void generatesNextWeekOnSaturdayOnceLastTrainingDayIsDone() {
        LocalDate saturday = LocalDate.of(2026, 11, 14);
        assertEquals(OptionalInt.of(2), WeekMath.targetWeek(saturday, START, 1, () -> true));
        assertEquals(OptionalInt.of(2), WeekMath.targetWeek(saturday.plusDays(1), START, 1, () -> true));
    }

    @Test
    void waitsWhenFridayIsNotDone() {
        assertTrue(WeekMath.targetWeek(LocalDate.of(2026, 11, 14), START, 1, () -> false).isEmpty());
    }

    @Test
    void doesNotGenerateMidWeek() {
        // Gate must not even be evaluated on weekdays.
        assertTrue(WeekMath.targetWeek(LocalDate.of(2026, 11, 12), START, 1, () -> {
            throw new AssertionError("gate evaluated on a weekday");
        }).isEmpty());
    }

    @Test
    void doesNotRegenerateWhenNextWeekAlreadyStored() {
        assertTrue(WeekMath.targetWeek(LocalDate.of(2026, 11, 15), START, 2, () -> true).isEmpty());
    }

    @Test
    void catchesUpWhenStoredPlanIsBehindTheCalendar() {
        assertEquals(OptionalInt.of(2), WeekMath.targetWeek(LocalDate.of(2026, 11, 16), START, 1, () -> false));
        assertEquals(OptionalInt.of(3), WeekMath.targetWeek(LocalDate.of(2026, 11, 24), START, 1, () -> false));
    }
}
