package com.college.attendance.service;

import com.college.attendance.exception.BadRequestException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * A date range for a report, with a title: a week (Monday to Sunday), a month,
 * the whole semester ("Overall") or a custom range.
 */
public record ReportPeriod(LocalDate from, LocalDate to, String title) {

    private static final DateTimeFormatter D_MMM = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter D_MMM_YYYY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    /** The Monday-to-Sunday week that contains the date. */
    public static ReportPeriod week(LocalDate anyDay) {
        LocalDate monday = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        return new ReportPeriod(monday, sunday, "Week " + monday.format(D_MMM) + " - " + sunday.format(D_MMM_YYYY));
    }

    public static ReportPeriod month(YearMonth month) {
        return new ReportPeriod(month.atDay(1), month.atEndOfMonth(), month.format(MONTH));
    }

    /** From the semester start to today. */
    public static ReportPeriod overall(LocalDate semesterStart, LocalDate today) {
        return new ReportPeriod(semesterStart, today, "Overall (semester)");
    }

    public static ReportPeriod custom(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("Choose both a start and an end date.");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("The end date must not be before the start date.");
        }
        return new ReportPeriod(from, to, from.format(D_MMM_YYYY) + " - " + to.format(D_MMM_YYYY));
    }
}
