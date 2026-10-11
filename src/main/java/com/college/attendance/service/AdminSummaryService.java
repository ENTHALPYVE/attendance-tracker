package com.college.attendance.service;

import com.college.attendance.dto.AdminSummaryDto;
import com.college.attendance.model.Role;
import com.college.attendance.repository.CollegeClassRepository;
import com.college.attendance.repository.StudentRepository;
import com.college.attendance.repository.SubjectRepository;
import com.college.attendance.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Counts for the admin home page. */
@Service
@Transactional(readOnly = true)
public class AdminSummaryService {

    private final CollegeClassRepository classRepository;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;

    public AdminSummaryService(CollegeClassRepository classRepository, StudentRepository studentRepository,
                               SubjectRepository subjectRepository, UserRepository userRepository) {
        this.classRepository = classRepository;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
    }

    public AdminSummaryDto summary() {
        return new AdminSummaryDto(
                classRepository.count(),
                studentRepository.count(),
                subjectRepository.count(),
                userRepository.countByRole(Role.TEACHER));
    }
}
