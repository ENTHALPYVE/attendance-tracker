package com.college.attendance;

import com.college.attendance.config.DataSeeder;
import com.college.attendance.model.Setting;
import com.college.attendance.repository.SettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Day and month reports built from real marks, teacher access rules and the PDF downloads.
 *
 * One day (a recent weekday) for a small class:
 *   slot 1  AOA theory (teacher A)   - roll 2 absent
 *   slot 2  Java lab for B1 (teacher B) - all present
 *   slot 3  COA lab for B2 (teacher C)  - all present
 * B1 = rolls 1, 2   B2 = roll 3.  So B1 has a free slot 3 and B2 a free slot 2.
 */
class ReportsTest extends ApiTestSupport {

    @Autowired
    private ObjectMapper json;
    @Autowired
    private SettingRepository settingRepository;

    private MockHttpSession admin, teacherA, teacherB, outsider;
    private long classId;
    private LocalDate date;
    private final Map<String, Long> students = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        admin = login("admin@college.edu", DataSeeder.ADMIN_PASSWORD);
        Map<String, Long> subjects = new HashMap<>();
        for (JsonNode s : call(admin, get("/api/admin/subjects"), null, 200)) {
            subjects.put(s.get("code").asText(), s.get("id").asLong());
        }
        date = LocalDate.now().minusWeeks(1);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.minusDays(1);
        }

        JsonNode cls = call(admin, post("/api/admin/classes"),
                Map.of("name", "RP-" + UUID.randomUUID().toString().substring(0, 8), "batchNames", List.of("B1", "B2")), 201);
        classId = cls.get("id").asLong();
        long b1 = cls.get("batches").get(0).get("id").asLong(), b2 = cls.get("batches").get(1).get("id").asLong();
        for (String[] st : new String[][]{{"1", "Asha", "B1"}, {"2", "Bala", "B1"}, {"3", "Chetan", "B2"}}) {
            students.put(st[0], call(admin, post("/api/admin/classes/" + classId + "/students"),
                    Map.of("rollNo", st[0], "name", st[1], "batchId", st[2].equals("B1") ? b1 : b2), 201).get("id").asLong());
        }

        String[] emails = new String[4];
        long[] ids = new long[4];
        for (int i = 0; i < 4; i++) {
            emails[i] = "rp" + UUID.randomUUID().toString().substring(0, 8) + "@college.edu";
            ids[i] = call(admin, post("/api/admin/teachers"),
                    Map.of("name", "Teacher " + i, "email", emails[i], "password", "secret1"), 201).get("id").asLong();
        }
        call(admin, post("/api/admin/timetable"), entry(1, subjects.get("AOA"), "THEORY", null, ids[0]), 201);
        call(admin, post("/api/admin/timetable"), entry(2, subjects.get("Java"), "LAB", b1, ids[1]), 201);
        call(admin, post("/api/admin/timetable"), entry(3, subjects.get("COA"), "LAB", b2, ids[2]), 201);
        teacherA = login(emails[0], "secret1");
        teacherB = login(emails[1], "secret1");
        outsider = login(emails[3], "secret1");

        // mark the day (admin may mark any session)
        for (JsonNode s : call(admin, get("/api/classes/" + classId + "/day?date=" + date), null, 200).get("sessions")) {
            List<Long> absent = s.get("slotNo").asInt() == 1 ? List.of(students.get("2")) : List.of();
            call(admin, put("/api/sessions/" + s.get("id").asLong() + "/attendance"), Map.of("absentStudentIds", absent), 200);
        }
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode call(MockHttpSession user, MockHttpServletRequestBuilder request, Object body, int expected) throws Exception {
        if (body != null) {
            request = withCsrf(request).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        String response = mvc.perform(request.session(user))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isEmpty() ? null : json.readTree(response);
    }

    private Map<String, Object> entry(int slot, long subjectId, String type, Long batchId, long teacherId) {
        Map<String, Object> m = new HashMap<>();
        m.put("classId", classId);
        m.put("dayOfWeek", date.getDayOfWeek().name());
        m.put("slotNo", slot);
        m.put("startTime", String.format("%02d:00", 8 + slot));
        m.put("endTime", String.format("%02d:00", 9 + slot));
        m.put("subjectId", subjectId);
        m.put("sessionType", type);
        m.put("batchId", batchId);
        m.put("teacherId", teacherId);
        return m;
    }

    private static JsonNode row(JsonNode report, String roll) {
        for (JsonNode r : report.get("rows")) {
            if (r.get("rollNo").asText().equals(roll)) return r;
        }
        throw new AssertionError("no row " + roll);
    }

    // ---------------------------------------------------------------- day report

    @Test
    void dayReportCountsOnlyTheStudentsOwnSessions() throws Exception {
        JsonNode r = call(admin, get("/api/reports/classes/" + classId + "/day?date=" + date), null, 200);
        assertThat(r.get("sessionsTaken").asInt()).isEqualTo(3);
        assertThat(r.get("sessionsPending").asInt()).isZero();

        JsonNode asha = row(r, "1");
        assertThat(asha.get("attended").asInt()).isEqualTo(2);
        assertThat(asha.get("held").asInt()).isEqualTo(2);       // AOA + Java lab, not the B2 COA lab
        assertThat(asha.get("percent").asDouble()).isEqualTo(100.0);
        assertThat(asha.get("freeSlots").asInt()).isEqualTo(1);  // slot 3

        JsonNode bala = row(r, "2");
        assertThat(bala.get("percent").asDouble()).isEqualTo(50.0);
        assertThat(bala.get("status").asText()).isEqualTo("DEFAULTER");
        assertThat(bala.get("absentIn").get(0).asText()).isEqualTo("AOA");

        JsonNode chetan = row(r, "3");
        assertThat(chetan.get("held").asInt()).isEqualTo(2);     // AOA + COA lab
    }

    @Test
    void freeSlotsCountAsPresentWhenTheSettingIsOn() throws Exception {
        try {
            settingRepository.save(new Setting(Setting.COUNT_FREE_SLOTS_AS_PRESENT, "true"));
            JsonNode r = call(admin, get("/api/reports/classes/" + classId + "/day?date=" + date), null, 200);
            assertThat(row(r, "2").get("attended").asInt()).isEqualTo(2);   // 1 + free slot
            assertThat(row(r, "2").get("held").asInt()).isEqualTo(3);
            assertThat(row(r, "2").get("percent").asDouble()).isEqualTo(66.6);   // 66.66.. cut to one decimal
        } finally {
            settingRepository.save(new Setting(Setting.COUNT_FREE_SLOTS_AS_PRESENT, "false"));
        }
    }

    // ---------------------------------------------------------------- month report

    @Test
    void monthReportHasSubjectColumnsAndTheTwoFormulas() throws Exception {
        String month = YearMonth.from(date).toString();
        JsonNode r = call(admin, get("/api/reports/classes/" + classId + "/month?month=" + month), null, 200);
        assertThat(r.get("subjects")).hasSize(3);   // AOA, COA, Java

        JsonNode bala = row(r, "2");
        assertThat(bala.get("attended").asInt()).isEqualTo(1);
        assertThat(bala.get("held").asInt()).isEqualTo(2);
        assertThat(bala.get("needed").asInt()).isEqualTo(2);         // ceil(3*2 - 4*1)
        assertThat(bala.get("canMiss").isNull()).isTrue();
        assertThat(bala.get("belowIn").get(0).asText()).isEqualTo("AOA Theory");

        JsonNode asha = row(r, "1");
        assertThat(asha.get("needed").isNull()).isTrue();
        assertThat(asha.get("canMiss").asInt()).isZero();              // floor(4*2/3 - 2) = 0
        assertThat(asha.get("status").asText()).isEqualTo("SAFE");
    }

    // ---------------------------------------------------------------- who sees what

    @Test
    void labOnlyTeacherSeesOnlyTheirBatchAndOthersSeeNothing() throws Exception {
        JsonNode forB = call(teacherB, get("/api/reports/classes/" + classId + "/day?date=" + date), null, 200);
        assertThat(forB.get("rows")).hasSize(2);   // B1 only
        assertThat(forB.get("sessionsTaken").asInt()).isEqualTo(2);   // AOA theory + own lab

        JsonNode forA = call(teacherA, get("/api/reports/classes/" + classId + "/day?date=" + date), null, 200);
        assertThat(forA.get("rows")).hasSize(3);   // theory teacher sees the whole class

        call(outsider, get("/api/reports/classes/" + classId + "/day?date=" + date), null, 403);
        JsonNode outsiderClasses = call(outsider, get("/api/reports/classes"), null, 200);
        assertThat(outsiderClasses).isEmpty();
    }

    // ---------------------------------------------------------------- week / overall / custom (Phase 6)

    @Test
    void weekOverallAndCustomRanges() throws Exception {
        JsonNode week = call(admin, get("/api/reports/classes/" + classId + "/range?period=WEEK&date=" + date), null, 200);
        assertThat(week.get("from").asText()).isEqualTo(date.with(DayOfWeek.MONDAY).toString());
        assertThat(week.get("to").asText()).isEqualTo(date.with(DayOfWeek.SUNDAY).toString());
        assertThat(row(week, "2").get("percent").asDouble()).isEqualTo(50.0);

        JsonNode overall = call(admin, get("/api/reports/classes/" + classId + "/range?period=OVERALL"), null, 200);
        assertThat(overall.get("title").asText()).contains("Overall");
        assertThat(overall.get("to").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(row(overall, "2").get("held").asInt()).isEqualTo(2);

        JsonNode custom = call(admin, get("/api/reports/classes/" + classId + "/range?period=CUSTOM&from=" + date
                + "&to=" + date), null, 200);
        assertThat(row(custom, "1").get("percent").asDouble()).isEqualTo(100.0);

        call(admin, get("/api/reports/classes/" + classId + "/range?period=CUSTOM&from=" + date + "&to=" + date.minusDays(1)), null, 400);
        call(admin, get("/api/reports/classes/" + classId + "/range?period=YEAR"), null, 400);
    }

    // ---------------------------------------------------------------- student detail (Phase 6)

    @Test
    void studentReportHasSubjectsDaysWeeksAndFormulas() throws Exception {
        JsonNode r = call(admin, get("/api/reports/students/" + students.get("2")), null, 200);
        assertThat(r.get("name").asText()).isEqualTo("Bala");
        assertThat(r.get("overall").get("attended").asInt()).isEqualTo(1);
        assertThat(r.get("overall").get("held").asInt()).isEqualTo(2);
        assertThat(r.get("overall").get("needed").asInt()).isEqualTo(2);
        assertThat(r.get("overall").get("status").asText()).isEqualTo("DEFAULTER");

        assertThat(r.get("days")).hasSize(1);
        assertThat(r.get("days").get(0).get("date").asText()).isEqualTo(date.toString());
        assertThat(r.get("days").get(0).get("absentIn").get(0).asText()).isEqualTo("AOA");
        assertThat(r.get("weeks")).hasSize(1);
        assertThat(r.get("weeks").get(0).get("cumulativePercent").asDouble()).isEqualTo(50.0);

        JsonNode aoa = null;
        for (JsonNode s : r.get("subjects")) if (s.get("code").asText().equals("AOA")) aoa = s;
        assertThat(aoa).isNotNull();
        assertThat(aoa.get("theoryHeld").asInt()).isEqualTo(1);
        assertThat(aoa.get("theoryAttended").asInt()).isZero();
        assertThat(aoa.get("defaulter").asBoolean()).isTrue();
    }

    @Test
    void studentReportFollowsTheBatchRule() throws Exception {
        call(teacherB, get("/api/reports/students/" + students.get("1")), null, 200);   // B1: teacher B's lab batch
        call(teacherB, get("/api/reports/students/" + students.get("3")), null, 403);   // B2: not theirs
        call(teacherA, get("/api/reports/students/" + students.get("3")), null, 200);   // theory teacher: whole class
        call(outsider, get("/api/reports/students/" + students.get("1")), null, 403);
    }

    // ---------------------------------------------------------------- teacher dashboard (Phase 6)

    @Test
    void dashboardCardsShowAverageDefaultersAndTaken() throws Exception {
        JsonNode cardsA = call(teacherA, get("/api/dashboard/teacher"), null, 200);
        assertThat(cardsA).hasSize(1);
        JsonNode aoa = cardsA.get(0);
        assertThat(aoa.get("sessionType").asText()).isEqualTo("THEORY");
        assertThat(aoa.get("students").asInt()).isEqualTo(3);
        assertThat(aoa.get("sessionsTaken").asInt()).isEqualTo(1);
        assertThat(aoa.get("averagePercent").asDouble()).isEqualTo(66.6);   // (100 + 0 + 100) / 3
        assertThat(aoa.get("defaulters").asInt()).isEqualTo(1);
        aoa.get("pending").forEach(p -> assertThat(p.get("date").asText()).isNotEqualTo(date.toString()));

        JsonNode cardsB = call(teacherB, get("/api/dashboard/teacher"), null, 200);
        assertThat(cardsB).hasSize(1);
        assertThat(cardsB.get(0).get("batchName").asText()).isEqualTo("B1");
        assertThat(cardsB.get(0).get("students").asInt()).isEqualTo(2);
        assertThat(cardsB.get(0).get("averagePercent").asDouble()).isEqualTo(100.0);
    }

    // ---------------------------------------------------------------- PDF

    @Test
    void pdfsDownload() throws Exception {
        MockHttpServletResponse day = mvc.perform(get("/api/reports/day.pdf?classId=" + classId + "&date=" + date).session(admin))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(day.getContentType()).isEqualTo("application/pdf");
        assertThat(day.getHeader("Content-Disposition")).contains("attendance-RP-").contains(date.toString());
        assertThat(new String(day.getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        MockHttpServletResponse month = mvc.perform(get("/api/reports/month.pdf?month=" + YearMonth.from(date)).session(admin))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(month.getHeader("Content-Disposition")).contains("attendance-all-classes-");
        assertThat(new String(month.getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        MockHttpServletResponse overall = mvc.perform(get("/api/reports/range.pdf?classId=" + classId + "&period=OVERALL").session(admin))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(overall.getHeader("Content-Disposition")).contains("-overall-");
        assertThat(new String(overall.getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        mvc.perform(get("/api/reports/month.pdf?month=Oct").session(admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/day.pdf?classId=" + classId).session(outsider)).andExpect(status().isForbidden());
    }
}
