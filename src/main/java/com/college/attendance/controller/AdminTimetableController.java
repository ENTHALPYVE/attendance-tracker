package com.college.attendance.controller;

import com.college.attendance.dto.TimetableDto;
import com.college.attendance.dto.TimetableEntryDto;
import com.college.attendance.dto.TimetableEntryRequest;
import com.college.attendance.service.TimetableService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Admin: the weekly timetable editor. */
@RestController
@RequestMapping("/api/admin")
public class AdminTimetableController {

    private final TimetableService timetableService;

    public AdminTimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    @GetMapping("/classes/{classId}/timetable")
    public TimetableDto timetable(@PathVariable Long classId) {
        return timetableService.getTimetable(classId);
    }

    @PostMapping("/timetable")
    @ResponseStatus(HttpStatus.CREATED)
    public TimetableEntryDto create(@Valid @RequestBody TimetableEntryRequest request) {
        return timetableService.create(request);
    }

    @PutMapping("/timetable/{id}")
    public TimetableEntryDto update(@PathVariable Long id, @Valid @RequestBody TimetableEntryRequest request) {
        return timetableService.update(id, request);
    }

    @DeleteMapping("/timetable/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        timetableService.delete(id);
    }
}
