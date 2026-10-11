package com.college.attendance.repository;

import com.college.attendance.model.Batch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    List<Batch> findByCollegeClassIdOrderByName(Long classId);

    boolean existsByCollegeClassIdAndNameIgnoreCase(Long classId, String name);
}
