package com.college.attendance.service;

import com.college.attendance.dto.HolidayDto;
import com.college.attendance.dto.HolidayRequest;
import com.college.attendance.exception.BadRequestException;
import com.college.attendance.exception.NotFoundException;
import com.college.attendance.model.ClassSession;
import com.college.attendance.model.Holiday;
import com.college.attendance.model.SessionStatus;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.ClassSessionRepository;
import com.college.attendance.repository.HolidayRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.college.attendance.util.Text.clean;

/**
 * Holidays. On a holiday all timetable sessions of that date are CANCELLED (so they do not count).
 *  - Sessions already created for the date are cancelled when the holiday is added.
 *  - Sessions created later for that date are created as CANCELLED (SessionGenerator).
 *  - Deleting the holiday reopens those sessions (TAKEN if attendance had been saved, else PENDING).
 * Extra sessions (not in the timetable) are left alone: they were added on purpose.
 */
@Service
@Transactional
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final ClassSessionRepository sessionRepository;
    private final AttendanceRecordRepository attendanceRepository;

    public HolidayService(HolidayRepository holidayRepository, ClassSessionRepository sessionRepository,
                          AttendanceRecordRepository attendanceRepository) {
        this.holidayRepository = holidayRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional(readOnly = true)
    public List<HolidayDto> list() {
        return holidayRepository.findAll(Sort.by("date")).stream()
                .map(h -> HolidayDto.from(h, (int) timetableSessions(h).stream()
                        .filter(s -> s.getStatus() == SessionStatus.CANCELLED).count()))
                .toList();
    }

    public HolidayDto add(HolidayRequest request) {
        if (holidayRepository.findByDate(request.date()).isPresent()) {
            throw new BadRequestException(request.date() + " is already a holiday.");
        }
        Holiday h = holidayRepository.save(new Holiday(request.date(), clean(request.reason())));
        List<ClassSession> sessions = timetableSessions(h);
        sessions.forEach(s -> s.setStatus(SessionStatus.CANCELLED));
        return HolidayDto.from(h, sessions.size());
    }

    public void delete(Long id) {
        Holiday h = holidayRepository.findById(id).orElseThrow(() -> new NotFoundException("Holiday not found."));
        for (ClassSession s : timetableSessions(h)) {
            if (s.getStatus() == SessionStatus.CANCELLED) {
                s.setStatus(attendanceRepository.existsBySessionId(s.getId()) ? SessionStatus.TAKEN : SessionStatus.PENDING);
            }
        }
        holidayRepository.delete(h);
    }

    /** The sessions of the holiday's date that come from the timetable (not extra sessions). */
    private List<ClassSession> timetableSessions(Holiday h) {
        return sessionRepository.findBySessionDate(h.getDate()).stream()
                .filter(s -> s.getTimetableEntry() != null)
                .toList();
    }
}
