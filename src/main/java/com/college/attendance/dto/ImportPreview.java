package com.college.attendance.dto;

import java.util.List;

/** Result of checking an uploaded student file. Nothing is saved until the admin confirms. */
public record ImportPreview(List<ImportRow> rows, int validCount, int errorCount, List<String> fileErrors) {

    public static ImportPreview of(List<ImportRow> rows, List<String> fileErrors) {
        int valid = (int) rows.stream().filter(ImportRow::valid).count();
        return new ImportPreview(rows, valid, rows.size() - valid, fileErrors);
    }

    public boolean canSave() {
        return fileErrors.isEmpty() && errorCount == 0 && validCount > 0;
    }
}
