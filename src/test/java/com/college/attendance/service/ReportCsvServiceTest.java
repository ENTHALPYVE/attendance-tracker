package com.college.attendance.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** CSV quoting and protection against formulas in names ("CSV injection"). */
class ReportCsvServiceTest {

    @Test
    void plainTextIsLeftAlone() {
        assertThat(ReportCsvService.cell("Aarav Mehta")).isEqualTo("Aarav Mehta");
        assertThat(ReportCsvService.cell("85.7")).isEqualTo("85.7");
        assertThat(ReportCsvService.cell(null)).isEmpty();
    }

    @Test
    void commasQuotesAndLineBreaksAreQuoted() {
        assertThat(ReportCsvService.cell("Rao, Kavya")).isEqualTo("\"Rao, Kavya\"");
        assertThat(ReportCsvService.cell("Sneha \"Sam\" D'Souza")).isEqualTo("\"Sneha \"\"Sam\"\" D'Souza\"");
        assertThat(ReportCsvService.cell("two\nlines")).isEqualTo("\"two\nlines\"");
    }

    @Test
    void formulasCannotRunInASpreadsheet() {
        assertThat(ReportCsvService.cell("=HYPERLINK(\"http://evil\")")).isEqualTo("\"'=HYPERLINK(\"\"http://evil\"\")\"");
        assertThat(ReportCsvService.cell("+91 98200")).isEqualTo("'+91 98200");
        assertThat(ReportCsvService.cell("-5")).isEqualTo("'-5");
        assertThat(ReportCsvService.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }
}
