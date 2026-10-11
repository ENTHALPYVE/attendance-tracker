package com.college.attendance.service.calc;

import com.college.attendance.model.SessionType;
import com.college.attendance.service.calc.AttendanceCalculator.Count;
import com.college.attendance.service.calc.AttendanceCalculator.Mark;
import com.college.attendance.service.calc.AttendanceCalculator.Status;
import com.college.attendance.service.calc.AttendanceCalculator.SubjectResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static com.college.attendance.model.SessionType.LAB;
import static com.college.attendance.model.SessionType.THEORY;
import static com.college.attendance.service.calc.AttendanceCalculator.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for every rule of the attendance calculation (no database, no Spring). */
class AttendanceCalculatorTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 12);
    private static final long AOA = 1, DSGT = 2, MATHS = 3, COA = 4, JAVA = 5, EDP = 6, ESE = 7;

    private static Mark mark(LocalDate d, int slot, long subject, SessionType type, boolean present) {
        return new Mark(d, slot, subject, type, present);
    }

    /**
     * The example Monday from the brief, for a B3 student:
     * slots 1-3 theory (AOA, DSGT, Maths), slot 4 Java Lab, slot 5 ESE practical.
     */
    private static List<Mark> b3Monday(boolean presentInJavaLab) {
        return List.of(
                mark(MON, 1, AOA, THEORY, true),
                mark(MON, 2, DSGT, THEORY, true),
                mark(MON, 3, MATHS, THEORY, true),
                mark(MON, 4, JAVA, LAB, presentInJavaLab),
                mark(MON, 5, ESE, LAB, true));
    }

    /** Same Monday for a B1 student: slot 4 AOA Lab, slot 5 FREE. */
    private static List<Mark> b1Monday() {
        return List.of(
                mark(MON, 1, AOA, THEORY, true),
                mark(MON, 2, DSGT, THEORY, true),
                mark(MON, 3, MATHS, THEORY, true),
                mark(MON, 4, AOA, LAB, true));
    }

    // ---------------------------------------------------------------- day-wise

    @Nested
    class DayWise {

        @Test
        void b3StudentAttendingEverythingIs100Percent() {
            Count c = count(b3Monday(true));
            assertThat(c).isEqualTo(new Count(5, 5));
            assertThat(c.percent()).isEqualTo(100.0);
        }

        @Test
        void b3StudentMissingJavaLabIs80Percent() {
            Count c = count(b3Monday(false));
            assertThat(c).isEqualTo(new Count(4, 5));
            assertThat(c.percent()).isEqualTo(80.0);
        }

        @Test
        void freeSlotIsTheSlotWhereOnlyOtherBatchesHadSessions() {
            Set<Integer> classSlots = Set.of(1, 2, 3, 4, 5);   // B2 and B3 had labs in slot 5
            Set<Integer> b1Slots = Set.of(1, 2, 3, 4);
            assertThat(freeSlots(classSlots, b1Slots)).isEqualTo(1);
            assertThat(freeSlots(classSlots, classSlots)).isZero();
        }

        @Test
        void freeSlotIsNotCountedByDefault() {
            Count c = withFreeSlots(count(b1Monday()), 1, false);
            assertThat(c).isEqualTo(new Count(4, 4));
        }

        @Test
        void freeSlotCountsAsAttendedWhenTheSettingIsOn() {
            Count c = withFreeSlots(count(b1Monday()), 1, true);
            assertThat(c).isEqualTo(new Count(5, 5));

            // absent in one session: 3/4 normally, 4/5 with the free slot
            List<Mark> missedOne = new ArrayList<>(b1Monday());
            missedOne.set(0, mark(MON, 1, AOA, THEORY, false));
            assertThat(withFreeSlots(count(missedOne), 1, false).percent()).isEqualTo(75.0);
            assertThat(withFreeSlots(count(missedOne), 1, true).percent()).isEqualTo(80.0);
        }
    }

    // ---------------------------------------------------------------- subject-wise

    @Nested
    class SubjectWise {

        @Test
        void theoryLabAndCombinedAreSeparate() {
            // AOA: 4 theory (3 attended) + 2 labs (1 attended)
            List<Mark> marks = List.of(
                    mark(MON, 1, AOA, THEORY, true),
                    mark(MON.plusDays(1), 2, AOA, THEORY, true),
                    mark(MON.plusDays(2), 3, AOA, THEORY, false),
                    mark(MON.plusDays(4), 1, AOA, THEORY, true),
                    mark(MON, 4, AOA, LAB, true),
                    mark(MON.plusDays(3), 4, AOA, LAB, false),
                    mark(MON, 2, DSGT, THEORY, true));
            Map<Long, SubjectResult> bySubject = bySubject(marks);

            SubjectResult aoa = bySubject.get(AOA);
            assertThat(aoa.theory()).isEqualTo(new Count(3, 4));
            assertThat(aoa.lab()).isEqualTo(new Count(1, 2));
            assertThat(aoa.combined()).isEqualTo(new Count(4, 6));
            assertThat(aoa.theory().percent()).isEqualTo(75.0);
            assertThat(aoa.lab().percent()).isEqualTo(50.0);

            assertThat(bySubject.get(DSGT).lab()).isEqualTo(Count.ZERO);
            assertThat(bySubject.get(DSGT).lab().percent()).isNull();
        }

        @Test
        void defaulterIfTheoryOrLabIsBelowThreshold() {
            SubjectResult goodTheoryBadLab = new SubjectResult(new Count(9, 10), new Count(1, 2));
            assertThat(isDefaulter(goodTheoryBadLab, 75)).isTrue();   // combined 10/12 = 83% but lab 50%

            SubjectResult bothFine = new SubjectResult(new Count(3, 4), new Count(4, 4));
            assertThat(isDefaulter(bothFine, 75)).isFalse();          // exactly 75% is not below

            SubjectResult theoryOnly = new SubjectResult(new Count(7, 10), Count.ZERO);
            assertThat(isDefaulter(theoryOnly, 75)).isTrue();         // missing lab part is ignored
        }
    }

    // ---------------------------------------------------------------- ranges

    @Test
    void rangeIsTotalAttendedOverTotalHeldWithFreeSlotsPerDay() {
        List<Mark> marks = new ArrayList<>(b1Monday());                // 4/4, one free slot
        marks.add(mark(MON.plusDays(1), 1, COA, THEORY, false));       // Tuesday 1/2
        marks.add(mark(MON.plusDays(1), 2, AOA, THEORY, true));
        Map<LocalDate, Integer> free = Map.of(MON, 1);

        assertThat(rangeTotal(marks, free, false)).isEqualTo(new Count(5, 6));
        assertThat(rangeTotal(marks, free, true)).isEqualTo(new Count(6, 7));
        assertThat(rangeTotal(List.of(), free, true)).isEqualTo(Count.ZERO);
    }

    // ---------------------------------------------------------------- status

    @Nested
    class StatusRule {

        @Test
        void belowThresholdIsDefaulter() {
            assertThat(status(new Count(74, 100), 75)).isEqualTo(Status.DEFAULTER);
        }

        @Test
        void thresholdToThresholdPlusFiveIsAtRisk() {
            assertThat(status(new Count(75, 100), 75)).isEqualTo(Status.AT_RISK);
            assertThat(status(new Count(80, 100), 75)).isEqualTo(Status.AT_RISK);
        }

        @Test
        void aboveThresholdPlusFiveIsSafe() {
            assertThat(status(new Count(81, 100), 75)).isEqualTo(Status.SAFE);
            assertThat(status(new Count(5, 5), 75)).isEqualTo(Status.SAFE);
        }

        @Test
        void nothingHeldIsNoData() {
            assertThat(status(Count.ZERO, 75)).isEqualTo(Status.NO_DATA);
        }

        @Test
        void usesTheThresholdFromSettings() {
            assertThat(status(new Count(70, 100), 65)).isEqualTo(Status.AT_RISK);
            assertThat(status(new Count(70, 100), 75)).isEqualTo(Status.DEFAULTER);
        }

        @Test
        void noRoundingErrorJustBelowThreshold() {
            // 2999/4000 = 74.975% : must be DEFAULTER and must not be displayed as 75.0
            Count c = new Count(2999, 4000);
            assertThat(status(c, 75)).isEqualTo(Status.DEFAULTER);
            assertThat(displayPercent(c)).isEqualTo(74.9);
        }
    }

    // ---------------------------------------------------------------- the two formulas

    @Nested
    class Formulas {

        @Test
        void sessionsNeededIsCeil3tMinus4p() {
            // t = 20, p = 12 (60%): 3*20 - 4*12 = 12 -> (12+12)/(20+12) = 24/32 = 75%
            assertThat(sessionsNeeded(new Count(12, 20), 75)).isEqualTo(12);
            // t = 10, p = 7 (70%): 3*10 - 4*7 = 2 -> 9/12 = 75%
            assertThat(sessionsNeeded(new Count(7, 10), 75)).isEqualTo(2);
            // t = 7, p = 5 (71.4%): 3*7 - 4*5 = 1 -> 6/8 = 75%
            assertThat(sessionsNeeded(new Count(5, 7), 75)).isEqualTo(1);
            // not needed when already at 75%
            assertThat(sessionsNeeded(new Count(3, 4), 75)).isNull();
        }

        @Test
        void sessionsNeededRoundsUp() {
            // t = 3, p = 2 (66.7%): 3*3 - 4*2 = 1, and t = 5, p = 3: 15 - 12 = 3
            assertThat(sessionsNeeded(new Count(2, 3), 75)).isEqualTo(1);
            assertThat(sessionsNeeded(new Count(3, 5), 75)).isEqualTo(3);
            // with threshold 80: (80*10 - 100*7) / 20 = 5 -> 12/15 = 80%
            assertThat(sessionsNeeded(new Count(7, 10), 80)).isEqualTo(5);
        }

        @Test
        void sessionsCanMissIsFloor4pOver3MinusT() {
            // t = 20, p = 18: 4*18/3 - 20 = 4 -> 18/24 = 75%
            assertThat(sessionsCanMiss(new Count(18, 20), 75)).isEqualTo(4);
            // t = 10, p = 9: 12 - 10 = 2 -> 9/12 = 75%
            assertThat(sessionsCanMiss(new Count(9, 10), 75)).isEqualTo(2);
            // t = 10, p = 8: 32/3 - 10 = 0.67 -> 0 (missing one would give 8/11 = 72.7%)
            assertThat(sessionsCanMiss(new Count(8, 10), 75)).isZero();
            // exactly 75%: can miss 0
            assertThat(sessionsCanMiss(new Count(3, 4), 75)).isZero();
            // below 75%: not applicable
            assertThat(sessionsCanMiss(new Count(2, 4), 75)).isNull();
        }

        @Test
        void formulasNeedSessionsHeld() {
            assertThat(sessionsNeeded(Count.ZERO, 75)).isNull();
            assertThat(sessionsCanMiss(Count.ZERO, 75)).isNull();
        }
    }
}
