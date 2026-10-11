package com.college.attendance.controller;

import com.college.attendance.dto.BatchDto;
import com.college.attendance.dto.BatchRequest;
import com.college.attendance.dto.ClassDto;
import com.college.attendance.dto.ClassRequest;
import com.college.attendance.service.ClassService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin: classes and their batches. */
@RestController
@RequestMapping("/api/admin")
public class AdminClassController {

    private final ClassService classService;

    public AdminClassController(ClassService classService) {
        this.classService = classService;
    }

    @GetMapping("/classes")
    public List<ClassDto> list() {
        return classService.listClasses();
    }

    @GetMapping("/classes/{id}")
    public ClassDto get(@PathVariable Long id) {
        return classService.getClass(id);
    }

    @PostMapping("/classes")
    @ResponseStatus(HttpStatus.CREATED)
    public ClassDto create(@Valid @RequestBody ClassRequest request) {
        return classService.createClass(request);
    }

    @PutMapping("/classes/{id}")
    public ClassDto rename(@PathVariable Long id, @Valid @RequestBody ClassRequest request) {
        return classService.renameClass(id, request);
    }

    @DeleteMapping("/classes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        classService.deleteClass(id);
    }

    @PostMapping("/classes/{classId}/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public BatchDto addBatch(@PathVariable Long classId, @Valid @RequestBody BatchRequest request) {
        return classService.addBatch(classId, request.name());
    }

    @PutMapping("/batches/{id}")
    public BatchDto renameBatch(@PathVariable Long id, @Valid @RequestBody BatchRequest request) {
        return classService.renameBatch(id, request.name());
    }

    @DeleteMapping("/batches/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBatch(@PathVariable Long id) {
        classService.deleteBatch(id);
    }
}
