package com.college.attendance.service.calc;

import com.college.attendance.model.SessionType;

import java.time.LocalDate;
import java.util.*;

/**
 * All attendance arithmetic, as plain functions (no database, no Spring) so it is easy to unit-test.
 *
 * Input for one student is a list of {@link Mark}s: one per TAKEN session the student was marked in
 * (PENDING and CANCELLED sessions are never passed in, so they never count).
 *
 *  - Theory %  = theory sessions attended / theory sessions held
 *  - Lab %     = lab sessions attended / lab sessions held for the student's batch
 *  - Combined% = (theory attended + lab attended) / (theory held + lab held)
 *  - Defaulter in a subject if Theory % or Lab % is below the threshold
 *  - Day %     = sessions attended that day / sessions held that day for the student's batch;
 *                optionally each FREE slot (the class had a session but this batch did not)
 *                counts as one attended session
 *  - Range %   = total attended / total held over all days in the range
 *  - Status    = DEFAULTER below threshold, AT_RISK from threshold to threshold + 5, SAFE above
 *  - Needed    = sessions in a row needed to reach the threshold  (75%: ceil(3t - 4p))
 *  - Can miss  = sessions that can be missed and stay at threshold (75%: floor(4p/3 - t))
 */
public final class AttendanceCalculator {

    private AttendanceCalculator() {
    }

    /** One taken session for one student. */
    public record Mark(LocalDate date, int slotNo, long subjectId, SessionType type, boolean present) {
    }

    /** "attended out of held". */
    public record Count(int attended, int held) {

        public static final Count ZERO = new Count(0, 0);

        public Count plus(Count other) {
            return new Count(attended + other.attended, held + other.held);
        }

        /** Percentage, or null when nothing was held (shown as "–"). */
        public Double percent() {
            return held == 0 ? null : attended * 100.0 / held;
        }
    }

    /** Theory, lab and combined counts of one subject. */
    public record SubjectResult(Count theory, Count lab) {

        public Count combined() {
            return theory.plus(lab);
        }
    }

    public enum Status { DEFAULTER, AT_RISK, SAFE, NO_DATA }

    // ---------------------------------------------------------------- totals

    /** Attended / held over the given marks. */
    public static Count count(Collection<Mark> marks) {
        int attended = 0;
        for (Mark m : marks) {
            if (m.present()) attended++;
        }
        return new Count(attended, marks.size());
    }

    /** Subject-wise theory and lab counts, keyed by subject id. */
    public static Map<Long, SubjectResult> bySubject(Collection<Mark> marks) {
        Map<Long, int[]> raw = new LinkedHashMap<>();   // {theoryAttended, theoryHeld, labAttended, labHeld}
        for (Mark m : marks) {
            int[] r = raw.computeIfAbsent(m.subjectId(), k -> new int[4]);
            int offset = m.type() == SessionType.THEORY ? 0 : 2;
            if (m.present()) r[offset]++;
            r[offset + 1]++;
        }
        Map<Long, SubjectResult> result = new LinkedHashMap<>();
        raw.forEach((subjectId, r) ->
                result.put(subjectId, new SubjectResult(new Count(r[0], r[1]), new Count(r[2], r[3]))));
        return result;
    }

    /** Defaulter in a subject: theory % or lab % below the threshold (a part that was never held is ignored). */
    public static boolean isDefaulter(SubjectResult r, int threshold) {
        return below(r.theory(), threshold) || below(r.lab(), threshold);
    }

    // ---------------------------------------------------------------- day-wise and free slots

    /**
     * Number of FREE slots for a batch on one day: slots in which the class had a taken session
     * but the batch had none (e.g. B1 in slot 5 while B2 and B3 are in labs).
     */
    public static int freeSlots(Set<Integer> classSlots, Set<Integer> batchSlots) {
        int free = 0;
        for (Integer slot : classSlots) {
            if (!batchSlots.contains(slot)) free++;
        }
        return free;
    }

    /** Adds free slots as attended sessions when the setting is on. */
    public static Count withFreeSlots(Count count, int freeSlots, boolean countFreeSlotsAsPresent) {
        return countFreeSlotsAsPresent ? count.plus(new Count(freeSlots, freeSlots)) : count;
    }

    /**
     * Total over a range, day by day: each day's marks plus (if the setting is on) that day's free slots.
     * freeSlotsByDate only needs entries for days the student was marked on.
     */
    public static Count rangeTotal(Collection<Mark> marks, Map<LocalDate, Integer> freeSlotsByDate,
                                   boolean countFreeSlotsAsPresent) {
        Map<LocalDate, List<Mark>> byDate = new TreeMap<>();
        for (Mark m : marks) {
            byDate.computeIfAbsent(m.date(), d -> new ArrayList<>()).add(m);
        }
        Count total = Count.ZERO;
        for (Map.Entry<LocalDate, List<Mark>> day : byDate.entrySet()) {
            int free = freeSlotsByDate.getOrDefault(day.getKey(), 0);
            total = total.plus(withFreeSlots(count(day.getValue()), free, countFreeSlotsAsPresent));
        }
        return total;
    }

    // ---------------------------------------------------------------- status and the two formulas

    /**
     * DEFAULTER below the threshold, AT_RISK from threshold to threshold + 5 (inclusive), SAFE above.
     * Compared with whole numbers (100 x attended vs threshold x held) so there are no rounding errors.
     */
    public static Status status(Count c, int threshold) {
        if (c.held() == 0) return Status.NO_DATA;
        long scaled = 100L * c.attended();
        if (scaled < (long) threshold * c.held()) return Status.DEFAULTER;
        if (scaled <= (long) (threshold + 5) * c.held()) return Status.AT_RISK;
        return Status.SAFE;
    }

    /**
     * How many more sessions (all attended) are needed to reach the threshold; null if already there.
     * From (p + x) / (t + x) >= T/100:  x >= (T*t - 100*p) / (100 - T).  For T = 75 this is ceil(3t - 4p).
     */
    public static Integer sessionsNeeded(Count c, int threshold) {
        if (c.held() == 0 || !below(c, threshold)) return null;
        long numerator = (long) threshold * c.held() - 100L * c.attended();
        long divisor = 100 - threshold;
        return (int) -Math.floorDiv(-numerator, divisor);   // ceiling division (Math.ceilDiv needs Java 18)
    }

    /**
     * How many sessions can be missed while staying at or above the threshold; null if below it.
     * From p / (t + x) >= T/100:  x <= (100*p - T*t) / T.  For T = 75 this is floor(4p/3 - t).
     */
    public static Integer sessionsCanMiss(Count c, int threshold) {
        if (c.held() == 0 || below(c, threshold)) return null;
        long numerator = 100L * c.attended() - (long) threshold * c.held();
        return (int) Math.floorDiv(numerator, threshold);
    }

    /**
     * Percentage for display, cut (not rounded) to one decimal place, so that e.g. 74.96% shows as
     * 74.9 and never as 75.0 next to a "Defaulter" badge.
     */
    public static Double displayPercent(Count c) {
        if (c.held() == 0) return null;
        return (1000L * c.attended() / c.held()) / 10.0;   // integer division = cut off
    }

    private static boolean below(Count c, int threshold) {
        return c.held() > 0 && 100L * c.attended() < (long) threshold * c.held();
    }
}
