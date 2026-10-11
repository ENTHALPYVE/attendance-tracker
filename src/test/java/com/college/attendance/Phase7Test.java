package com.college.attendance;

import com.college.attendance.config.DataSeeder;
import com.college.attendance.repository.HolidayRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7: Excel export, holidays, settings and substitute teachers.
 *
 * A small class with one recent day marked (same layout as ReportsTest):
 *   slot 1 AOA theory (teacher A, 09:00-10:00) - roll 2 absent
 *   slot 2 Java lab B1 (teacher B, 10:00-11:00)
 * B1 = rolls 1, 2   B2 = roll 3.
 */
class Phase7Test extends ApiTestSupport {

    @Autowired
    private ObjectMapper json;
    @Autowired
    private HolidayRepository holidayRepository;

    private MockHttpSession admin, teacherA, teacherB, teacherC;
    private long classId, teacherBId, teacherCId;
    private LocalDate date;
    private final Map<String, Long> students = new HashMap<>();
    private final Map<String, Long> subjects = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        admin = login("admin@college.edu", DataSeeder.ADMIN_PASSWORD);
        for (JsonNode s : call(admin, get("/api/admin/subjects"), null, 200)) {
            subjects.put(s.get("code").asText(), s.get("id").asLong());
        }
        date = LocalDate.now().minusWeeks(1);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.minusDays(1);
        }
        JsonNode cls = call(admin, post("/api/admin/classes"),
                Map.of("name", "P7-" + UUID.randomUUID().toString().substring(0, 8), "batchNames", List.of("B1", "B2")), 201);
        classId = cls.get("id").asLong();
        long b1 = cls.get("batches").get(0).get("id").asLong(), b2 = cls.get("batches").get(1).get("id").asLong();
        for (String[] st : new String[][]{{"1", "Asha", "B1"}, {"2", "Bala", "B1"}, {"3", "Chetan", "B2"}}) {
            students.put(st[0], call(admin, post("/api/admin/classes/" + classId + "/students"),
                    Map.of("rollNo", st[0], "name", st[1], "batchId", st[2].equals("B1") ? b1 : b2), 201).get("id").asLong());
        }
        String[] emails = new String[3];
        long[] ids = new long[3];
        for (int i = 0; i < 3; i++) {
            emails[i] = "p7" + UUID.randomUUID().toString().substring(0, 8) + "@college.edu";
            ids[i] = call(admin, post("/api/admin/teachers"),
                    Map.of("name", "Teacher " + "ABC".charAt(i), "email", emails[i], "password", "secret1"), 201).get("id").asLong();
        }
        teacherBId = ids[1];
        teacherCId = ids[2];
        call(admin, post("/api/admin/timetable"), entry(date.getDayOfWeek(), 1, "AOA", "THEORY", null, ids[0]), 201);
        call(admin, post("/api/admin/timetable"), entry(date.getDayOfWeek(), 2, "Java", "LAB", b1, ids[1]), 201);
        teacherA = login(emails[0], "secret1");
        teacherB = login(emails[1], "secret1");
        teacherC = login(emails[2], "secret1");

        for (JsonNode s : day(date).get("sessions")) {
            List<Long> absent = s.get("slotNo").asInt() == 1 ? List.of(students.get("2")) : List.of();
            call(admin, put("/api/sessions/" + s.get("id").asLong() + "/attendance"), Map.of("absentStudentIds", absent), 200);
        }
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode call(MockHttpSession user, MockHttpServletRequestBuilder request, Object body, int expected) throws Exception {
        if (body != null) {
            request = withCsrf(request).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        } else if (!"GET".equals(request.buildRequest(new org.springframework.mock.web.MockServletContext()).getMethod())) {
            request = withCsrf(request);
        }
        String response = mvc.perform(request.session(user))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isEmpty() ? null : json.readTree(response);
    }

    private Map<String, Object> entry(DayOfWeek day, int slot, String subject, String type, Long batchId, long teacherId) {
        Map<String, Object> m = new HashMap<>();
        m.put("classId", classId);
        m.put("dayOfWeek", day.name());
        m.put("slotNo", slot);
        m.put("startTime", String.format("%02d:00", 8 + slot));
        m.put("endTime", String.format("%02d:00", 9 + slot));
        m.put("subjectId", subjects.get(subject));
        m.put("sessionType", type);
        m.put("batchId", batchId);
        m.put("teacherId", teacherId);
        return m;
    }

    private JsonNode day(LocalDate d) throws Exception {
        return call(admin, get("/api/classes/" + classId + "/day?date=" + d), null, 200);
    }

    private Workbook excel(String url) throws Exception {
        byte[] bytes = mvc.perform(get(url).session(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    /** All text in the first column, from the first data row (row 6 for two heading rows, 5 for one). */
    private static List<String> names(Sheet sheet, int firstDataRow) {
        List<String> names = new ArrayList<>();
        for (int r = firstDataRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null && row.getCell(1) != null) names.add(row.getCell(1).getStringCellValue());
        }
        return names;
    }

    // ---------------------------------------------------------------- Excel

    @Test
    void classReportExcelHasTheoryLabCombinedColumnsAndNumbers() throws Exception {
        try (Workbook wb = excel("/api/reports/range.xlsx?classId=" + classId + "&period=WEEK&date=" + date)) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("Week");
            Row head1 = sheet.getRow(3), head2 = sheet.getRow(4);
            assertThat(head1.getCell(0).getStringCellValue()).isEqualTo("Roll No");
            assertThat(head1.getCell(3).getStringCellValue()).isEqualTo("AOA");
            assertThat(head2.getCell(3).getStringCellValue()).isEqualTo("T");
            assertThat(names(sheet, 5)).containsExactly("Asha", "Bala", "Chetan");

            Row bala = sheet.getRow(6);
            int overallCol = head1.getLastCellNum() - 3;   // ... Attended, Held, Overall %, Status, Note
            assertThat(head1.getCell(overallCol).getStringCellValue()).isEqualTo("Overall %");
            assertThat(bala.getCell(overallCol).getNumericCellValue()).isEqualTo(50.0);   // a number, not text
        }
    }

    @Test
    void defaulterExcelListsOnlyDefaulters() throws Exception {
        try (Workbook wb = excel("/api/reports/defaulters.xlsx?classId=" + classId + "&period=WEEK&date=" + date)) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(names(sheet, 4)).containsExactly("Bala");
            assertThat(sheet.getRow(4).getCell(7).getStringCellValue()).isEqualTo("AOA Theory");
            assertThat(sheet.getRow(4).getCell(8).getNumericCellValue()).isEqualTo(2.0);   // ceil(3*2 - 4*1)
        }
    }

    @Test
    void dayExcelDownloads() throws Exception {
        try (Workbook wb = excel("/api/reports/day.xlsx?classId=" + classId + "&date=" + date)) {
            assertThat(names(wb.getSheetAt(0), 4)).containsExactly("Asha", "Bala", "Chetan");
        }
    }

    // ---------------------------------------------------------------- holidays

    @Test
    void holidayCancelsTheDaysSessionsAndDeletingItRestoresThem() throws Exception {
        if (holidayRepository.findByDate(date).isPresent()) {
            return;   // the seeded holiday happens to be this date; nothing to test
        }
        JsonNode h = call(admin, post("/api/admin/holidays"), Map.of("date", date.toString(), "reason", "Test holiday"), 201);
        assertThat(h.get("cancelledSessions").asInt()).isGreaterThanOrEqualTo(2);
        day(date).get("sessions").forEach(s -> assertThat(s.get("status").asText()).isEqualTo("CANCELLED"));
        assertThat(call(admin, get("/api/reports/classes/" + classId + "/day?date=" + date), null, 200)
                .get("sessionsTaken").asInt()).isZero();

        call(admin, post("/api/admin/holidays"), Map.of("date", date.toString(), "reason", "Again"), 400);

        call(admin, delete("/api/admin/holidays/" + h.get("id").asLong()), null, 204);
        day(date).get("sessions").forEach(s -> assertThat(s.get("status").asText()).isEqualTo("TAKEN"));
    }

    // ---------------------------------------------------------------- settings

    @Test
    void settingsAreValidatedAndUsed() throws Exception {
        JsonNode before = call(admin, get("/api/settings"), null, 200);
        try {
            JsonNode bad = call(admin, put("/api/admin/settings"),
                    Map.of("threshold", 40, "countFreeSlotsAsPresent", false, "semesterStart", before.get("semesterStart").asText()), 400);
            assertThat(bad.get("fieldErrors").get("threshold").asText()).contains("between 50 and 95");

            call(admin, put("/api/admin/settings"),
                    Map.of("threshold", 40 + 20, "countFreeSlotsAsPresent", false, "semesterStart", before.get("semesterStart").asText()), 200);
            JsonNode report = call(admin, get("/api/reports/classes/" + classId + "/day?date=" + date), null, 200);
            assertThat(report.get("threshold").asInt()).isEqualTo(60);

            call(teacherA, put("/api/admin/settings"),
                    Map.of("threshold", 70, "countFreeSlotsAsPresent", false, "semesterStart", before.get("semesterStart").asText()), 403);
        } finally {
            call(admin, put("/api/admin/settings"), Map.of("threshold", before.get("threshold").asInt(),
                    "countFreeSlotsAsPresent", before.get("countFreeSlotsAsPresent").asBoolean(),
                    "semesterStart", before.get("semesterStart").asText()), 200);
        }
    }

    // ---------------------------------------------------------------- substitute teacher

    @Test
    void substituteTakesOverASession() throws Exception {
        long theory = day(date).get("sessions").get(0).get("id").asLong();   // teacher A's AOA theory
        JsonNode moved = call(admin, put("/api/admin/sessions/" + theory + "/teacher"),
                Map.of("teacherId", teacherCId), 200);
        assertThat(moved.get("teacherName").asText()).isEqualTo("Teacher C");

        call(teacherA, get("/api/sessions/" + theory + "/attendance"), null, 403);   // no longer theirs
        call(teacherC, put("/api/sessions/" + theory + "/attendance"), Map.of("absentStudentIds", List.of()), 200);

        JsonNode mine = call(teacherC, get("/api/me/sessions?date=" + date), null, 200);
        assertThat(mine.findValuesAsText("id")).contains(String.valueOf(theory));
    }

    @Test
    void substituteWhoIsBusyGivesAClashWarning() throws Exception {
        JsonNode sessions = day(date).get("sessions");
        long theory = sessions.get(0).get("id").asLong();   // 09:00-10:00
        long lab = sessions.get(1).get("id").asLong();      // 10:00-11:00, teacher B

        // back-to-back is not a clash: B may also take the 09:00 lecture
        call(admin, put("/api/admin/sessions/" + theory + "/teacher"), Map.of("teacherId", teacherBId), 200);

        // teacher C has an extra session 09:30-10:30 that day, which overlaps both
        call(admin, post("/api/sessions/extra"), Map.of("classId", classId, "date", date.toString(), "slotNo", 7,
                "startTime", "09:30", "endTime", "10:30", "subjectId", subjects.get("AOA"), "sessionType", "THEORY",
                "teacherId", teacherCId), 201);
        JsonNode clash = call(admin, put("/api/admin/sessions/" + lab + "/teacher"), Map.of("teacherId", teacherCId), 409);
        assertThat(clash.get("clash").asBoolean()).isTrue();
        assertThat(clash.get("message").asText()).contains("Teacher C already has");

        JsonNode anyway = call(admin, put("/api/admin/sessions/" + lab + "/teacher"),
                Map.of("teacherId", teacherCId, "allowClash", true), 200);
        assertThat(anyway.get("teacherName").asText()).isEqualTo("Teacher C");
    }

    @Test
    void substituteCanBeArrangedForAnUpcomingDay() throws Exception {
        LocalDate upcoming = date.plusWeeks(1);
        if (!upcoming.isAfter(LocalDate.now())) upcoming = upcoming.plusWeeks(1);
        JsonNode planned = day(upcoming).get("sessions").get(0);
        assertThat(planned.get("id").isNull()).isTrue();

        Map<String, Object> request = new HashMap<>(Map.of("teacherId", teacherCId,
                "timetableEntryId", planned.get("timetableEntryId").asLong(), "date", upcoming.toString()));
        JsonNode arranged = call(admin, post("/api/admin/sessions/upcoming"), request, 200);
        assertThat(arranged.get("teacherName").asText()).isEqualTo("Teacher C");

        JsonNode after = day(upcoming).get("sessions");
        assertThat(after).hasSize(2);   // the arranged session + the still-planned lab
        assertThat(after.get(0).get("id").isNull()).isFalse();
        assertThat(after.get(0).get("teacherName").asText()).isEqualTo("Teacher C");

        request.put("date", LocalDate.now().plusDays(30).toString());
        call(admin, post("/api/admin/sessions/upcoming"), request, 400);
        call(teacherA, post("/api/admin/sessions/upcoming"), request, 403);
    }
}
