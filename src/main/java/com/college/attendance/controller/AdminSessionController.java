package com.college.attendance.controller;

import com.college.attendance.dto.SessionDto;
import com.college.attendance.dto.SubstituteRequest;
import com.college.attendance.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Admin: give a session to a substitute teacher. */
@RestController
@RequestMapping("/api/admin/sessions")
public class AdminSessionController {

    private final SessionService sessionService;

    public AdminSessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** An existing session (today or earlier, or one arranged before). */
    @PutMapping("/{id}/teacher")
    public SessionDto reassign(@PathVariable Long id, @Valid @RequestBody SubstituteRequest request) {
        return sessionService.reassign(id, request.teacherId(), request.allowClash());
    }

    /** An upcoming day: needs timetableEntryId and date. */
    @PostMapping("/upcoming")
    public SessionDto reassignUpcoming(@Valid @RequestBody SubstituteRequest request) {
        return sessionService.reassignUpcoming(request.timetableEntryId(), request.date(), request.teacherId(),
                request.allowClash());
    }
}
