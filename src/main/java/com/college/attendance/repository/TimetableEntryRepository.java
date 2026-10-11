package com.college.attendance.repository;

import com.college.attendance.model.TimetableEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.DayOfWeek;
import java.util.List;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, Long> {

    /** The whole week of one class, with subject, batch and teacher loaded. */
    @EntityGraph(attributePaths = {"subject", "batch", "teacher"})
    List<TimetableEntry> findByCollegeClassId(Long classId);

    /** One class on one weekday (shown as "planned" for future dates). */
    @EntityGraph(attributePaths = {"collegeClass", "subject", "batch", "teacher"})
    List<TimetableEntry> findByCollegeClassIdAndDayOfWeek(Long classId, DayOfWeek day);

    /** One weekday across all classes (used to create that day's sessions). */
    @EntityGraph(attributePaths = {"collegeClass", "subject", "batch", "teacher"})
    List<TimetableEntry> findByDayOfWeek(DayOfWeek day);

    /** Everything a teacher has on one weekday, in any class (used for clash checks). */
    @EntityGraph(attributePaths = {"collegeClass", "subject", "batch"})
    List<TimetableEntry> findByTeacherIdAndDayOfWeek(Long teacherId, DayOfWeek day);

    /** Rows of [classId, sessionType, batchId or null]: what a teacher teaches. */
    @Query("select distinct e.collegeClass.id, e.sessionType, b.id from TimetableEntry e "
            + "left join e.batch b where e.teacher.id = :teacherId")
    List<Object[]> teachingOf(Long teacherId);

    /** Rows of [classId, subjectId, sessionType, batchId or null]: the groups a teacher teaches. */
    @Query("select distinct e.collegeClass.id, e.subject.id, e.sessionType, b.id from TimetableEntry e "
            + "left join e.batch b where e.teacher.id = :teacherId")
    List<Object[]> groupsOf(Long teacherId);

    /** The entries in one cell of a class's timetable. */
    List<TimetableEntry> findByCollegeClassIdAndDayOfWeekAndSlotNo(Long classId, DayOfWeek day, int slotNo);

    boolean existsByCollegeClassId(Long classId);

    boolean existsByBatchId(Long batchId);

    boolean existsBySubjectId(Long subjectId);

    boolean existsByTeacherId(Long teacherId);
}
