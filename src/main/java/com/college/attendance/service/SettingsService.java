package com.college.attendance.service;

import com.college.attendance.model.Setting;
import com.college.attendance.repository.SettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** Reads values from the settings table, with safe defaults. */
@Service
@Transactional(readOnly = true)
public class SettingsService {

    private final SettingRepository settingRepository;

    public SettingsService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    /** Minimum attendance percentage (default 75). */
    public int threshold() {
        return value(Setting.THRESHOLD).map(v -> {
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return 75;
            }
        }).orElse(75);
    }

    /** Whether a free slot counts as an attended session in day-wise figures (default false). */
    public boolean countFreeSlotsAsPresent() {
        return value(Setting.COUNT_FREE_SLOTS_AS_PRESENT).map(v -> v.trim().equalsIgnoreCase("true")).orElse(false);
    }

    /** First day of the semester, if set. Sessions are never created before this date. */
    public Optional<LocalDate> semesterStart() {
        return value(Setting.SEMESTER_START).flatMap(v -> {
            try {
                return Optional.of(LocalDate.parse(v.trim()));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        });
    }

    /** Saves the attendance rules (admin). */
    @Transactional
    public void update(int threshold, boolean countFreeSlotsAsPresent, LocalDate semesterStart) {
        settingRepository.save(new Setting(Setting.THRESHOLD, String.valueOf(threshold)));
        settingRepository.save(new Setting(Setting.COUNT_FREE_SLOTS_AS_PRESENT, String.valueOf(countFreeSlotsAsPresent)));
        settingRepository.save(new Setting(Setting.SEMESTER_START, semesterStart.toString()));
    }

    private Optional<String> value(String key) {
        return settingRepository.findById(key).map(Setting::getValue);
    }
}
