package com.college.attendance.controller;

import com.college.attendance.dto.TeacherDto;
import com.college.attendance.dto.TeacherRequest;
import com.college.attendance.service.TeacherService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin: teacher accounts. */
@RestController
@RequestMapping("/api/admin/teachers")
public class AdminTeacherController {

    private final TeacherService teacherService;

    public AdminTeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @GetMapping
    public List<TeacherDto> list() {
        return teacherService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherDto create(@Valid @RequestBody TeacherRequest request) {
        return teacherService.create(request);
    }

    @PutMapping("/{id}")
    public TeacherDto update(@PathVariable Long id, @Valid @RequestBody TeacherRequest request) {
        return teacherService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        teacherService.delete(id);
    }
}
