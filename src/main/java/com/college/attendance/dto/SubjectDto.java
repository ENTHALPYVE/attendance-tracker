package com.college.attendance.dto;

import com.college.attendance.model.Subject;

public record SubjectDto(Long id, String code, String name, boolean hasTheory, boolean hasLab) {

    public static SubjectDto from(Subject s) {
        return new SubjectDto(s.getId(), s.getCode(), s.getName(), s.isHasTheory(), s.isHasLab());
    }
}
