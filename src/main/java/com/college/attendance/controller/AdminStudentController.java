package com.college.attendance.controller;

import com.college.attendance.dto.ImportConfirmRequest;
import com.college.attendance.dto.ImportPreview;
import com.college.attendance.dto.StudentDto;
import com.college.attendance.dto.StudentRequest;
import com.college.attendance.service.StudentImportService;
import com.college.attendance.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Admin: students of a class, single and bulk (Excel / CSV upload). */
@RestController
@RequestMapping("/api/admin")
public class AdminStudentController {

    private final StudentService studentService;
    private final StudentImportService importService;

    public AdminStudentController(StudentService studentService, StudentImportService importService) {
        this.studentService = studentService;
        this.importService = importService;
    }

    @GetMapping("/classes/{classId}/students")
    public List<StudentDto> list(@PathVariable Long classId) {
        return studentService.listByClass(classId);
    }

    @PostMapping("/classes/{classId}/students")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentDto add(@PathVariable Long classId, @Valid @RequestBody StudentRequest request) {
        return studentService.add(classId, request);
    }

    /** Edit roll number / name, or move to another batch. */
    @PutMapping("/students/{id}")
    public StudentDto update(@PathVariable Long id, @Valid @RequestBody StudentRequest request) {
        return studentService.update(id, request);
    }

    @DeleteMapping("/students/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        studentService.delete(id);
    }

    /** Step 1 of the upload: check the file and return a preview. Nothing is saved. */
    @PostMapping(value = "/classes/{classId}/students/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreview preview(@PathVariable Long classId, @RequestParam("file") MultipartFile file) {
        return importService.preview(classId, file);
    }

    /** Step 2 of the upload: save the previewed rows. Returns 400 with the preview if any row has an error. */
    @PostMapping("/classes/{classId}/students/import")
    public ResponseEntity<ImportPreview> save(@PathVariable Long classId,
                                              @Valid @RequestBody ImportConfirmRequest request) {
        ImportPreview result = importService.save(classId, request);
        return result.canSave() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    /** An empty Excel file with the right column headings. */
    @GetMapping("/students/template.xlsx")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("students-template.xlsx").build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(importService.template());
    }
}
