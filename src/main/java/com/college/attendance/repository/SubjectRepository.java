package com.college.attendance.repository;

import com.college.attendance.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findAllByOrderByCode();

    boolean existsByCodeIgnoreCase(String code);
}
