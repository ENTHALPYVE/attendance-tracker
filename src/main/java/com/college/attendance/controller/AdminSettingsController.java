package com.college.attendance.controller;

import com.college.attendance.dto.HolidayDto;
import com.college.attendance.dto.HolidayRequest;
import com.college.attendance.dto.SettingsDto;
import com.college.attendance.dto.SettingsRequest;
import com.college.attendance.service.HolidayService;
import com.college.attendance.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin: holidays and attendance rules (threshold, free slots, semester start). */
@RestController
@RequestMapping("/api/admin")
public class AdminSettingsController {

    private final HolidayService holidayService;
    private final SettingsService settingsService;

    public AdminSettingsController(HolidayService holidayService, SettingsService settingsService) {
        this.holidayService = holidayService;
        this.settingsService = settingsService;
    }

    @GetMapping("/holidays")
    public List<HolidayDto> holidays() {
        return holidayService.list();
    }

    @PostMapping("/holidays")
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayDto addHoliday(@Valid @RequestBody HolidayRequest request) {
        return holidayService.add(request);
    }

    @DeleteMapping("/holidays/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHoliday(@PathVariable Long id) {
        holidayService.delete(id);
    }

    @PutMapping("/settings")
    public SettingsDto updateSettings(@Valid @RequestBody SettingsRequest request) {
        settingsService.update(request.threshold(), request.countFreeSlotsAsPresent(), request.semesterStart());
        return new SettingsDto(settingsService.threshold(), settingsService.countFreeSlotsAsPresent(),
                settingsService.semesterStart().orElse(null));
    }
}
