package com.college.attendance.service;

import com.college.attendance.dto.StudentDto;
import com.college.attendance.dto.StudentRequest;
import com.college.attendance.exception.BadRequestException;
import com.college.attendance.exception.NotFoundException;
import com.college.attendance.model.Batch;
import com.college.attendance.model.CollegeClass;
import com.college.attendance.model.Student;
import com.college.attendance.repository.StudentRepository;
import com.college.attendance.util.RollNumbers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.college.attendance.util.Text.clean;

/** Adding, editing, moving (changing batch) and removing single students. */
@Service
@Transactional
public class StudentService {

    private final StudentRepository studentRepository;
    private final ClassService classService;

    public StudentService(StudentRepository studentRepository, ClassService classService) {
        this.studentRepository = studentRepository;
        this.classService = classService;
    }

    /** Students of a class, sorted by roll number. */
    @Transactional(readOnly = true)
    public List<StudentDto> listByClass(Long classId) {
        classService.findClass(classId);
        return studentRepository.findByCollegeClassId(classId).stream()
                .sorted(RollNumbers.BY_ROLL_NO)
                .map(StudentDto::from)
                .toList();
    }

    public StudentDto add(Long classId, StudentRequest request) {
        CollegeClass c = classService.findClass(classId);
        Batch batch = batchOfClass(request.batchId(), classId);
        String rollNo = clean(request.rollNo());
        checkRollNoFree(classId, rollNo, null);
        Student s = studentRepository.save(new Student(rollNo, clean(request.name()), c, batch));
        return StudentDto.from(s);
    }

    /** Edits roll number and name, and moves the student if the batch changed. */
    public StudentDto update(Long id, StudentRequest request) {
        Student s = find(id);
        Long classId = s.getCollegeClass().getId();
        String rollNo = clean(request.rollNo());
        checkRollNoFree(classId, rollNo, id);
        s.setRollNo(rollNo);
        s.setName(clean(request.name()));
        s.setBatch(batchOfClass(request.batchId(), classId));
        return StudentDto.from(s);
    }

    /** Removes the student and (through ON DELETE CASCADE) their attendance records. */
    public void delete(Long id) {
        studentRepository.delete(find(id));
    }

    /** The class of a student (used to work out the report period). */
    @Transactional(readOnly = true)
    public Long classIdOf(Long studentId) {
        return find(studentId).getCollegeClass().getId();
    }

    private Student find(Long id) {
        return studentRepository.findById(id).orElseThrow(() -> new NotFoundException("Student not found."));
    }

    private Batch batchOfClass(Long batchId, Long classId) {
        Batch batch = classService.findBatch(batchId);
        if (!batch.getCollegeClass().getId().equals(classId)) {
            throw new BadRequestException("That batch belongs to another class.");
        }
        return batch;
    }

    private void checkRollNoFree(Long classId, String rollNo, Long exceptStudentId) {
        studentRepository.findByCollegeClassIdAndRollNoIgnoreCase(classId, rollNo)
                .filter(other -> !other.getId().equals(exceptStudentId))
                .ifPresent(other -> {
                    throw new BadRequestException("Roll number " + rollNo + " is already used by " + other.getName() + ".");
                });
    }
}
