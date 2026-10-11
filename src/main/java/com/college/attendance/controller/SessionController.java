package com.college.attendance.controller;

import com.college.attendance.dto.*;
import com.college.attendance.security.AppUserPrincipal;
import com.college.attendance.service.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything a teacher needs to take attendance. Available to any logged-in user;
 * who may change which session is checked in SessionService (own sessions, or admin).
 */
@RestController
@RequestMapping("/api")
public class SessionController {

    private final SessionQueryService queryService;
    private final SessionService sessionService;
    private final AttendanceService attendanceService;
    private final SessionGenerator generator;
    private final ClassService classService;
    private final SubjectService subjectService;
    private final Clock clock;

    public SessionController(SessionQueryService queryService, SessionService sessionService,
                             AttendanceService attendanceService, SessionGenerator generator,
                             ClassService classService, SubjectService subjectService, Clock clock) {
        this.queryService = queryService;
        this.sessionService = sessionService;
        this.attendanceService = attendanceService;
        this.generator = generator;
        this.classService = classService;
        this.subjectService = subjectService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- viewing

    /** Classes with their batches (for the class picker and the day grid columns). */
    @GetMapping("/classes")
    public List<ClassDto> classes() {
        return classService.listClasses();
    }

    /** Subjects (for the extra-session form). */
    @GetMapping("/subjects")
    public List<SubjectDto> subjects() {
        return subjectService.list();
    }

    /** One class on one date (default today). */
    @GetMapping("/classes/{classId}/day")
    public DayDto classDay(@PathVariable Long classId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queryService.classDay(classId, orToday(date));
    }

    /** "My sessions today": the logged-in teacher's sessions on a date (default today), all classes. */
    @GetMapping("/me/sessions")
    public List<SessionDto> mySessions(@AuthenticationPrincipal AppUserPrincipal user,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queryService.teacherDay(user.getId(), orToday(date));
    }

    // ---------------------------------------------------------------- marking

    @GetMapping("/sessions/{id}/attendance")
    public AttendanceSheetDto sheet(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
        return attendanceService.sheet(id, user);
    }

    @PutMapping("/sessions/{id}/attendance")
    public AttendanceSheetDto save(@PathVariable Long id, @Valid @RequestBody SaveAttendanceRequest request,
                                   @AuthenticationPrincipal AppUserPrincipal user) {
        return attendanceService.save(id, request, user);
    }

    // ---------------------------------------------------------------- cancel / reopen / extra

    @PostMapping("/sessions/{id}/cancel")
    public SessionDto cancel(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
        return sessionService.cancel(id, user);
    }

    @PostMapping("/sessions/{id}/reopen")
    public SessionDto reopen(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
        return sessionService.reopen(id, user);
    }

    @PostMapping("/sessions/extra")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionDto createExtra(@Valid @RequestBody ExtraSessionRequest request,
                                  @AuthenticationPrincipal AppUserPrincipal user) {
        // Create the day's timetable sessions first (own transaction), so the slot check sees them.
        generator.ensureSessions(request.date());
        return sessionService.createExtra(request, user);
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExtra(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
        sessionService.deleteExtra(id, user);
    }

    private LocalDate orToday(LocalDate date) {
        return date == null ? LocalDate.now(clock) : date;
    }
}
