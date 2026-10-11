package com.college.attendance.service;

import com.college.attendance.dto.SubjectDto;
import com.college.attendance.dto.SubjectRequest;
import com.college.attendance.exception.BadRequestException;
import com.college.attendance.exception.NotFoundException;
import com.college.attendance.model.Subject;
import com.college.attendance.repository.ClassSessionRepository;
import com.college.attendance.repository.SubjectRepository;
import com.college.attendance.repository.TimetableEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.college.attendance.util.Text.clean;

@Service
@Transactional
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final TimetableEntryRepository timetableRepository;
    private final ClassSessionRepository sessionRepository;

    public SubjectService(SubjectRepository subjectRepository, TimetableEntryRepository timetableRepository,
                          ClassSessionRepository sessionRepository) {
        this.subjectRepository = subjectRepository;
        this.timetableRepository = timetableRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public List<SubjectDto> list() {
        return subjectRepository.findAllByOrderByCode().stream().map(SubjectDto::from).toList();
    }

    public SubjectDto create(SubjectRequest request) {
        String code = clean(request.code());
        if (subjectRepository.existsByCodeIgnoreCase(code)) {
            throw new BadRequestException("A subject with code " + code + " already exists.");
        }
        Subject s = new Subject(code, clean(request.name()), request.hasTheory(), request.hasLab());
        return SubjectDto.from(subjectRepository.save(s));
    }

    public SubjectDto update(Long id, SubjectRequest request) {
        Subject s = find(id);
        String code = clean(request.code());
        if (!code.equalsIgnoreCase(s.getCode()) && subjectRepository.existsByCodeIgnoreCase(code)) {
            throw new BadRequestException("A subject with code " + code + " already exists.");
        }
        s.setCode(code);
        s.setName(clean(request.name()));
        s.setHasTheory(request.hasTheory());
        s.setHasLab(request.hasLab());
        return SubjectDto.from(s);
    }

    public void delete(Long id) {
        Subject s = find(id);
        if (timetableRepository.existsBySubjectId(id) || sessionRepository.existsBySubjectId(id)) {
            throw new BadRequestException(s.getCode() + " is used in the timetable or attendance records and cannot be deleted.");
        }
        subjectRepository.delete(s);
    }

    public Subject find(Long id) {
        return subjectRepository.findById(id).orElseThrow(() -> new NotFoundException("Subject not found."));
    }
}
