package com.fitTrack.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;

/** Calendar-week arithmetic for the challenge. Weeks run Monday to Sunday. */
public final class WeekMath {

    private WeekMath() {}

    public static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** 1-based challenge week. The week containing the start date is week 1, even if the start is mid-week. */
    public static int weekFor(LocalDate start, LocalDate today) {
        long weeks = ChronoUnit.WEEKS.between(mondayOf(start), mondayOf(today));
        return (int) Math.max(1, weeks + 1);
    }

    /** Monday of the given challenge week. */
    public static LocalDate weekStart(LocalDate start, int week) {
        return mondayOf(start).plusWeeks(Math.max(1, week) - 1L);
    }

    /**
     * Which week's plan should be generated today, if any.
     * <ul>
     *   <li>The stored plan is behind the calendar: catch up to the current week.</li>
     *   <li>It is Saturday or Sunday and the stored plan is for the current week: build next week, but
     *       only once the last scheduled training day has been completed.</li>
     * </ul>
     */
    public static OptionalInt targetWeek(
            LocalDate today, LocalDate start, int storedWeek, BooleanSupplier lastTrainingDayDone) {
        int current = weekFor(start, today);
        if (storedWeek < current) {
            return OptionalInt.of(current);
        }
        boolean weekend = today.getDayOfWeek() == DayOfWeek.SATURDAY || today.getDayOfWeek() == DayOfWeek.SUNDAY;
        if (storedWeek == current && weekend && lastTrainingDayDone.getAsBoolean()) {
            return OptionalInt.of(current + 1);
        }
        return OptionalInt.empty();
    }
}
