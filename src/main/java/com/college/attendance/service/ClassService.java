package com.college.attendance.service;

import com.college.attendance.dto.BatchDto;
import com.college.attendance.dto.ClassDto;
import com.college.attendance.dto.ClassRequest;
import com.college.attendance.exception.BadRequestException;
import com.college.attendance.exception.NotFoundException;
import com.college.attendance.model.Batch;
import com.college.attendance.model.CollegeClass;
import com.college.attendance.repository.BatchRepository;
import com.college.attendance.repository.ClassSessionRepository;
import com.college.attendance.repository.CollegeClassRepository;
import com.college.attendance.repository.StudentRepository;
import com.college.attendance.repository.TimetableEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.college.attendance.util.Text.clean;

/** Classes (e.g. SE-CO-A) and their batches (B1, B2, B3). */
@Service
@Transactional
public class ClassService {

    private final CollegeClassRepository classRepository;
    private final BatchRepository batchRepository;
    private final StudentRepository studentRepository;
    private final TimetableEntryRepository timetableRepository;
    private final ClassSessionRepository sessionRepository;

    public ClassService(CollegeClassRepository classRepository, BatchRepository batchRepository,
                        StudentRepository studentRepository, TimetableEntryRepository timetableRepository,
                        ClassSessionRepository sessionRepository) {
        this.classRepository = classRepository;
        this.batchRepository = batchRepository;
        this.studentRepository = studentRepository;
        this.timetableRepository = timetableRepository;
        this.sessionRepository = sessionRepository;
    }

    // ---------------------------------------------------------------- classes

    @Transactional(readOnly = true)
    public List<ClassDto> listClasses() {
        Map<Long, Long> countByClass = toCountMap(studentRepository.countPerClass());
        return classRepository.findAllByOrderByName().stream()
                .map(c -> new ClassDto(c.getId(), c.getName(), countByClass.getOrDefault(c.getId(), 0L),
                        batchesOf(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClassDto getClass(Long id) {
        CollegeClass c = findClass(id);
        return new ClassDto(c.getId(), c.getName(), studentRepository.countByCollegeClassId(id), batchesOf(id));
    }

    public ClassDto createClass(ClassRequest request) {
        String name = clean(request.name());
        if (classRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("A class named " + name + " already exists.");
        }
        CollegeClass c = classRepository.save(new CollegeClass(name));

        Set<String> seen = new HashSet<>();
        for (String raw : Optional.ofNullable(request.batchNames()).orElse(List.of())) {
            String batchName = clean(raw);
            if (batchName.isEmpty() || !seen.add(batchName.toUpperCase())) {
                continue;   // skip blanks and repeats such as "B1, B1"
            }
            if (batchName.length() > 20) {
                throw new BadRequestException("Batch name " + batchName + " is too long (max 20 characters).");
            }
            batchRepository.save(new Batch(c, batchName));
        }
        return getClass(c.getId());
    }

    public ClassDto renameClass(Long id, ClassRequest request) {
        CollegeClass c = findClass(id);
        String name = clean(request.name());
        if (!name.equalsIgnoreCase(c.getName()) && classRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("A class named " + name + " already exists.");
        }
        c.setName(name);
        return getClass(id);
    }

    /** A class can only be deleted while it has no students, timetable or sessions. */
    public void deleteClass(Long id) {
        CollegeClass c = findClass(id);
        if (studentRepository.countByCollegeClassId(id) > 0) {
            throw new BadRequestException("Remove the students of " + c.getName() + " before deleting it.");
        }
        if (timetableRepository.existsByCollegeClassId(id) || sessionRepository.existsByCollegeClassId(id)) {
            throw new BadRequestException(c.getName() + " has a timetable or attendance records and cannot be deleted.");
        }
        batchRepository.deleteAll(batchRepository.findByCollegeClassIdOrderByName(id));
        classRepository.delete(c);
    }

    // ---------------------------------------------------------------- batches

    public BatchDto addBatch(Long classId, String rawName) {
        CollegeClass c = findClass(classId);
        String name = clean(rawName);
        if (batchRepository.existsByCollegeClassIdAndNameIgnoreCase(classId, name)) {
            throw new BadRequestException(c.getName() + " already has a batch " + name + ".");
        }
        Batch b = batchRepository.save(new Batch(c, name));
        return new BatchDto(b.getId(), b.getName(), 0);
    }

    public BatchDto renameBatch(Long batchId, String rawName) {
        Batch b = findBatch(batchId);
        String name = clean(rawName);
        Long classId = b.getCollegeClass().getId();
        if (!name.equalsIgnoreCase(b.getName()) && batchRepository.existsByCollegeClassIdAndNameIgnoreCase(classId, name)) {
            throw new BadRequestException("This class already has a batch " + name + ".");
        }
        b.setName(name);
        return new BatchDto(b.getId(), b.getName(), studentRepository.countByBatchId(batchId));
    }

    /** A batch can only be deleted while it has no students, lab timetable entries or sessions. */
    public void deleteBatch(Long batchId) {
        Batch b = findBatch(batchId);
        long students = studentRepository.countByBatchId(batchId);
        if (students > 0) {
            throw new BadRequestException("Batch " + b.getName() + " has " + students
                    + " students. Move or remove them first.");
        }
        if (timetableRepository.existsByBatchId(batchId) || sessionRepository.existsByBatchId(batchId)) {
            throw new BadRequestException("Batch " + b.getName() + " is used in the timetable or attendance records.");
        }
        batchRepository.delete(b);
    }

    // ---------------------------------------------------------------- helpers

    public CollegeClass findClass(Long id) {
        return classRepository.findById(id).orElseThrow(() -> new NotFoundException("Class not found."));
    }

    public Batch findBatch(Long id) {
        return batchRepository.findById(id).orElseThrow(() -> new NotFoundException("Batch not found."));
    }

    private List<BatchDto> batchesOf(Long classId) {
        Map<Long, Long> countByBatch = toCountMap(studentRepository.countPerBatch(classId));
        return batchRepository.findByCollegeClassIdOrderByName(classId).stream()
                .map(b -> new BatchDto(b.getId(), b.getName(), countByBatch.getOrDefault(b.getId(), 0L)))
                .toList();
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }
}
