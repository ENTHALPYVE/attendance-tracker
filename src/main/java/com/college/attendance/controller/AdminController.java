package com.college.attendance.controller;

import com.college.attendance.dto.AdminSummaryDto;
import com.college.attendance.service.AdminSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin home page data. Everything under /api/admin is ADMIN-only (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminSummaryService summaryService;

    public AdminController(AdminSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/summary")
    public AdminSummaryDto summary() {
        return summaryService.summary();
    }
}
