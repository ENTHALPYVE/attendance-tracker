package com.college.attendance.repository;

import com.college.attendance.model.CollegeClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollegeClassRepository extends JpaRepository<CollegeClass, Long> {

    List<CollegeClass> findAllByOrderByName();

    boolean existsByNameIgnoreCase(String name);
}
