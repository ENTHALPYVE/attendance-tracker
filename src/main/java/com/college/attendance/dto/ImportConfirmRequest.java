package com.college.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** The previewed rows sent back by the browser when the admin clicks Save. They are checked again. */
public record ImportConfirmRequest(@NotEmpty(message = "There are no students to save.") @Valid List<Row> rows) {

    public record Row(String rollNo, String name, String batch) {
    }
}
