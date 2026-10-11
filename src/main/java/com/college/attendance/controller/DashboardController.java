package com.college.attendance.controller;

import com.college.attendance.dto.SettingsDto;
import com.college.attendance.dto.TeacherCardDto;
import com.college.attendance.security.AppUserPrincipal;
import com.college.attendance.service.DashboardService;
import com.college.attendance.service.SettingsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Teacher dashboard cards and the read-only settings the pages need. */
@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;
    private final SettingsService settingsService;

    public DashboardController(DashboardService dashboardService, SettingsService settingsService) {
        this.dashboardService = dashboardService;
        this.settingsService = settingsService;
    }

    /** One card per subject / batch the logged-in teacher teaches. */
    @GetMapping("/dashboard/teacher")
    public List<TeacherCardDto> teacherCards(@AuthenticationPrincipal AppUserPrincipal user) {
        return dashboardService.teacherCards(user);
    }

    @GetMapping("/settings")
    public SettingsDto settings() {
        return new SettingsDto(settingsService.threshold(), settingsService.countFreeSlotsAsPresent(),
                settingsService.semesterStart().orElse(null));
    }
}
