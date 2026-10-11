package com.college.attendance.dto;

import java.util.List;

public record ClassDto(Long id, String name, long studentCount, List<BatchDto> batches) {
}
