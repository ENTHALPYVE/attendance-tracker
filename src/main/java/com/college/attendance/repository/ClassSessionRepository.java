package com.college.attendance.repository;

import com.college.attendance.model.ClassSession;
import com.college.attendance.model.SessionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    List<ClassSession> findBySessionDate(LocalDate date);

    /** One session with the related records loaded. */
    @EntityGraph(attributePaths = {"collegeClass", "subject", "batch", "teacher"})
    Optional<ClassSession> findWithDetailsById(Long id);

    /** A teacher's sessions on one date, in any class. */
    @EntityGraph(attributePaths = {"collegeClass", "subject", "batch", "teacher"})
    List<ClassSession> findByTeacherIdAndSessionDate(Long teacherId, LocalDate date);

    /** The sessions in one slot of a class on a date. */
    List<ClassSession> findByCollegeClassIdAndSessionDateAndSlotNo(Long classId, LocalDate date, int slotNo);

    /** One class's sessions on one date, with the related records loaded. */
    @EntityGraph(attributePaths = {"collegeClass", "subject", "batch", "teacher"})
    List<ClassSession> findByCollegeClassIdAndSessionDate(Long classId, LocalDate date);

    /** All sessions of a class in a date range (any status), with subject and batch loaded. */
    @EntityGraph(attributePaths = {"subject", "batch"})
    List<ClassSession> findByCollegeClassIdAndSessionDateBetween(Long classId, LocalDate from, LocalDate to);

    /** Rows of [classId, sessionType, batchId or null]: what a teacher has taught. */
    @Query("select distinct s.collegeClass.id, s.sessionType, b.id from ClassSession s "
            + "left join s.batch b where s.teacher.id = :teacherId")
    List<Object[]> teachingOf(Long teacherId);

    /** Rows of [classId, subjectId, sessionType, batchId or null]: the groups a teacher has taken. */
    @Query("select distinct s.collegeClass.id, s.subject.id, s.sessionType, b.id from ClassSession s "
            + "left join s.batch b where s.teacher.id = :teacherId")
    List<Object[]> groupsOf(Long teacherId);

    /** The earliest session date of a class (used when no semester start is set). */
    @Query("select min(s.sessionDate) from ClassSession s where s.collegeClass.id = :classId")
    Optional<LocalDate> firstDateOf(Long classId);

    /** Sessions created from a timetable entry, from a date onwards, with a given status. */
    List<ClassSession> findByTimetableEntryIdAndSessionDateGreaterThanEqualAndStatus(
            Long entryId, LocalDate from, SessionStatus status);

    boolean existsByCollegeClassId(Long classId);

    boolean existsByBatchId(Long batchId);

    boolean existsBySubjectId(Long subjectId);

    boolean existsByTeacherId(Long teacherId);
}
