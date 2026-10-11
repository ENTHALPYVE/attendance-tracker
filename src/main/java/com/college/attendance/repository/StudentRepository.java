package com.college.attendance.repository;

import com.college.attendance.model.Student;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    /** Students of a class with their batch loaded in the same query. */
    @EntityGraph(attributePaths = "batch")
    List<Student> findByCollegeClassId(Long classId);

    /** Students of one batch, with their batch loaded. */
    @EntityGraph(attributePaths = "batch")
    List<Student> findByBatchId(Long batchId);

    Optional<Student> findByCollegeClassIdAndRollNoIgnoreCase(Long classId, String rollNo);

    long countByBatchId(Long batchId);

    long countByCollegeClassId(Long classId);

    /** Rows of [batchId, studentCount] for one class. */
    @Query("select s.batch.id, count(s) from Student s where s.collegeClass.id = :classId group by s.batch.id")
    List<Object[]> countPerBatch(Long classId);

    /** Rows of [classId, studentCount] for all classes. */
    @Query("select s.collegeClass.id, count(s) from Student s group by s.collegeClass.id")
    List<Object[]> countPerClass();
}
