package com.college.attendance.dto;

import java.util.List;

/**
 * One row of an uploaded student file, as shown in the preview.
 * rowNo is the line number in the file; errors is empty when the row is fine.
 */
public record ImportRow(int rowNo, String rollNo, String name, String batch, List<String> errors) {

    public boolean valid() {
        return errors.isEmpty();
    }
}
