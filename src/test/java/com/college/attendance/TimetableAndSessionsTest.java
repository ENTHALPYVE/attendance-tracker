package com.college.attendance;

import com.college.attendance.config.DataSeeder;
import com.college.attendance.model.Holiday;
import com.college.attendance.model.SessionStatus;
import com.college.attendance.repository.ClassSessionRepository;
import com.college.attendance.repository.HolidayRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 3: timetable rules, teacher clashes and creating sessions from the timetable. */
class TimetableAndSessionsTest extends ApiTestSupport {

    @Autowired
    private ObjectMapper json;
    @Autowired
    private ClassSessionRepository sessionRepository;
    @Autowired
    private HolidayRepository holidayRepository;

    private MockHttpSession admin;
    private final Map<String, Long> subjectIds = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        admin = login("admin@college.edu", DataSeeder.ADMIN_PASSWORD);
        for (JsonNode s : get("/api/admin/subjects")) {
            subjectIds.put(s.get("code").asText(), s.get("id").asLong());
        }
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode get(String url) throws Exception {
        return json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                        .session(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode send(MockHttpServletRequestBuilder request, Object body, int expectedStatus) throws Exception {
        String response = mvc.perform(withCsrf(request).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isEmpty() ? null : json.readTree(response);
    }

    /** A new class with batches B1 and B2. */
    private JsonNode newClass() throws Exception {
        return send(post("/api/admin/classes"), Map.of("name", "TT-" + UUID.randomUUID().toString().substring(0, 8),
                "batchNames", List.of("B1", "B2")), 201);
    }

    private long newTeacher() throws Exception {
        String email = "t" + UUID.randomUUID().toString().substring(0, 8) + "@college.edu";
        return send(post("/api/admin/teachers"), Map.of("name", "Teacher " + email, "email", email,
                "password", "secret1"), 201).get("id").asLong();
    }

    private static long batch(JsonNode cls, String name) {
        for (JsonNode b : cls.get("batches")) {
            if (b.get("name").asText().equals(name)) return b.get("id").asLong();
        }
        throw new AssertionError(name);
    }

    private Map<String, Object> entry(JsonNode cls, DayOfWeek day, int slot, String start, String end,
                                      String subject, String type, Long batchId, long teacherId) {
        Map<String, Object> m = new HashMap<>();
        m.put("classId", cls.get("id").asLong());
        m.put("dayOfWeek", day.name());
        m.put("slotNo", slot);
        m.put("startTime", start);
        m.put("endTime", end);
        m.put("subjectId", subjectIds.get(subject));
        m.put("sessionType", type);
        m.put("batchId", batchId);
        m.put("teacherId", teacherId);
        m.put("room", "R1");
        return m;
    }

    /** A recent weekday in the past (inside the seeded semester). */
    private static LocalDate pastWeekday(int weeksBack) {
        LocalDate d = LocalDate.now().minusWeeks(weeksBack);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.minusDays(1);
        }
        return d;
    }

    private JsonNode sessions(JsonNode cls, LocalDate date) throws Exception {
        return get("/api/classes/" + cls.get("id").asLong() + "/day?date=" + date).get("sessions");
    }

    // ---------------------------------------------------------------- cell rules

    @Test
    void theoryNeedsTheWholeSlot() throws Exception {
        JsonNode cls = newClass();
        long t1 = newTeacher(), t2 = newTeacher();
        send(post("/api/admin/timetable"), entry(cls, DayOfWeek.MONDAY, 1, "09:00", "10:00", "AOA", "THEORY", null, t1), 201);

        JsonNode secondTheory = send(post("/api/admin/timetable"),
                entry(cls, DayOfWeek.MONDAY, 1, "09:00", "10:00", "DSGT", "THEORY", null, t2), 400);
        assertThat(secondTheory.get("message").asText()).contains("already has entries");

        JsonNode labOverTheory = send(post("/api/admin/timetable"),
                entry(cls, DayOfWeek.MONDAY, 1, "09:00", "10:00", "Java", "LAB", batch(cls, "B1"), t2), 400);
        assertThat(labOverTheory.get("message").asText()).contains("AOA theory for the whole class");
    }

    @Test
    void oneLabPerBatchInASlot() throws Exception {
        JsonNode cls = newClass();
        long t1 = newTeacher(), t2 = newTeacher(), t3 = newTeacher();
        send(post("/api/admin/timetable"), entry(cls, DayOfWeek.TUESDAY, 4, "13:00", "15:00", "AOA", "LAB", batch(cls, "B1"), t1), 201);
        send(post("/api/admin/timetable"), entry(cls, DayOfWeek.TUESDAY, 4, "13:00", "15:00", "Java", "LAB", batch(cls, "B2"), t2), 201);

        JsonNode sameBatch = send(post("/api/admin/timetable"),
                entry(cls, DayOfWeek.TUESDAY, 4, "13:00", "15:00", "COA", "LAB", batch(cls, "B1"), t3), 400);
        assertThat(sameBatch.get("message").asText()).contains("lab for batch B1");

        send(post("/api/admin/timetable"),
                entry(cls, DayOfWeek.TUESDAY, 4, "13:00", "15:00", "DSGT", "THEORY", null, t3), 400);

        assertThat(get("/api/admin/classes/" + cls.get("id").asLong() + "/timetable").get("entries")).hasSize(2);
    }

    @Test
    void subjectTypeBatchAndTimesAreChecked() throws Exception {
        JsonNode cls = newClass();
        long t = newTeacher();
        assertThat(send(post("/api/admin/timetable"),
                entry(cls, DayOfWeek.MONDAY, 2, "10:00", "11:00", "DSGT", "LAB", batch(cls, "B1"), t), 400)
                .get("message").asText()).contains("DSGT has no lab part");
        assertThat(send(post("/api/admin/timetable"),
                entry(cls, DayOfWeek.MONDAY, 2, "10:00", "11:00", "Java", "THEORY", null, t), 400)
                .get("message").asText()).contains("Java has no theory part");
        assertThat(send(post("/api/admin/timetable"),
                entry(cls, DayOfWeek.MONDAY, 2, "10:00", "11:00", "AOA", "LAB", null, t), 400)
                .get("message").asText()).contains("Choose the batch");
        assertThat(send(post("/api/admin/timetable"),
                entry(cls, DayOfWeek.MONDAY, 2, "11:00", "10:00", "AOA", "THEORY", null, t), 400)
                .get("message").asText()).contains("End time must be after start time");

        Map<String, Object> missing = entry(cls, DayOfWeek.MONDAY, 2, "10:00", "11:00", "AOA", "THEORY", null, t);
        missing.put("teacherId", null);
        assertThat(send(post("/api/admin/timetable"), missing, 400)
                .get("fieldErrors").get("teacherId").asText()).isEqualTo("Choose a teacher.");
    }

    // ---------------------------------------------------------------- teacher clashes

    @Test
    void teacherClashIsReportedAndCanBeOverridden() throws Exception {
        JsonNode classA = newClass(), classB = newClass();
        long t = newTeacher();
        send(post("/api/admin/timetable"), entry(classA, DayOfWeek.WEDNESDAY, 1, "09:00", "10:00", "AOA", "THEORY", null, t), 201);

        JsonNode clash = send(post("/api/admin/timetable"),
                entry(classB, DayOfWeek.WEDNESDAY, 1, "09:30", "10:30", "AOA", "THEORY", null, t), 409);
        assertThat(clash.get("clash").asBoolean()).isTrue();
        assertThat(clash.get("message").asText()).contains(classA.get("name").asText()).contains("Wednesday");

        // back-to-back is not a clash
        send(post("/api/admin/timetable"), entry(classB, DayOfWeek.WEDNESDAY, 2, "10:00", "11:00", "AOA", "THEORY", null, t), 201);

        Map<String, Object> override = entry(classB, DayOfWeek.WEDNESDAY, 1, "09:30", "10:30", "AOA", "THEORY", null, t);
        override.put("allowClash", true);
        send(post("/api/admin/timetable"), override, 201);
    }

    @Test
    void editingAnEntryIsNotAClashWithItself() throws Exception {
        JsonNode cls = newClass();
        long t = newTeacher();
        JsonNode e = send(post("/api/admin/timetable"),
                entry(cls, DayOfWeek.THURSDAY, 1, "09:00", "10:00", "AOA", "THEORY", null, t), 201);
        Map<String, Object> changed = entry(cls, DayOfWeek.THURSDAY, 1, "09:00", "10:00", "AOA", "THEORY", null, t);
        changed.put("room", "Room 999");
        JsonNode saved = send(put("/api/admin/timetable/" + e.get("id").asLong()), changed, 200);
        assertThat(saved.get("room").asText()).isEqualTo("Room 999");
    }

    // ---------------------------------------------------------------- sessions from the timetable

    @Test
    void sessionsAreCreatedOnceFromTheTimetable() throws Exception {
        JsonNode cls = newClass();
        long t1 = newTeacher(), t2 = newTeacher(), t3 = newTeacher();
        LocalDate date = pastWeekday(1);
        DayOfWeek day = date.getDayOfWeek();
        send(post("/api/admin/timetable"), entry(cls, day, 1, "09:00", "10:00", "AOA", "THEORY", null, t1), 201);
        send(post("/api/admin/timetable"), entry(cls, day, 2, "10:00", "12:00", "AOA", "LAB", batch(cls, "B1"), t1), 201);
        send(post("/api/admin/timetable"), entry(cls, day, 2, "10:00", "12:00", "Java", "LAB", batch(cls, "B2"), t2), 201);
        send(post("/api/admin/timetable"), entry(cls, day.plus(1), 1, "09:00", "10:00", "DSGT", "THEORY", null, t3), 201);

        JsonNode first = sessions(cls, date);
        assertThat(first).hasSize(3);
        assertThat(first.get(0).get("sessionType").asText()).isEqualTo("THEORY");
        assertThat(first.get(0).get("status").asText()).isEqualTo("PENDING");
        assertThat(first.get(1).get("batchName").asText()).isEqualTo("B1");
        assertThat(first.get(2).get("subjectCode").asText()).isEqualTo("Java");
        assertThat(first.get(0).get("extra").asBoolean()).isFalse();

        JsonNode again = sessions(cls, date);
        assertThat(again).hasSize(3);
        assertThat(again.get(0).get("id").asLong()).isEqualTo(first.get(0).get("id").asLong());
    }

    @Test
    void futureDatesShowThePlanButCreateNoSessions() throws Exception {
        JsonNode cls = newClass();
        LocalDate future = LocalDate.now().plusDays(7);
        send(post("/api/admin/timetable"),
                entry(cls, future.getDayOfWeek(), 1, "09:00", "10:00", "AOA", "THEORY", null, newTeacher()), 201);
        JsonNode day = get("/api/classes/" + cls.get("id").asLong() + "/day?date=" + future);
        assertThat(day.get("open").asBoolean()).isFalse();
        assertThat(day.get("sessions")).hasSize(1);                       // shown as planned
        assertThat(day.get("sessions").get(0).get("id").isNull()).isTrue();  // but not created
        assertThat(sessionRepository.findByCollegeClassIdAndSessionDate(cls.get("id").asLong(), future)).isEmpty();
    }

    @Test
    void sessionsOnAHolidayAreCancelled() throws Exception {
        JsonNode cls = newClass();
        LocalDate date = pastWeekday(2);
        if (holidayRepository.findByDate(date).isEmpty()) {
            holidayRepository.save(new Holiday(date, "Test holiday"));
        }
        send(post("/api/admin/timetable"),
                entry(cls, date.getDayOfWeek(), 3, "11:00", "12:00", "AOA", "THEORY", null, newTeacher()), 201);

        JsonNode list = sessions(cls, date);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void changingAnEntryUpdatesTodaysPendingSession() throws Exception {
        JsonNode cls = newClass();
        long t1 = newTeacher(), t2 = newTeacher();
        LocalDate today = LocalDate.now();
        JsonNode e = send(post("/api/admin/timetable"),
                entry(cls, today.getDayOfWeek(), 1, "09:00", "10:00", "AOA", "THEORY", null, t1), 201);
        assertThat(sessions(cls, today).get(0).get("teacherId").asLong()).isEqualTo(t1);

        send(put("/api/admin/timetable/" + e.get("id").asLong()),
                entry(cls, today.getDayOfWeek(), 1, "09:00", "10:00", "AOA", "THEORY", null, t2), 200);
        JsonNode after = sessions(cls, today);
        assertThat(after).hasSize(1);
        assertThat(after.get(0).get("teacherId").asLong()).isEqualTo(t2);
    }

    @Test
    void deletingAnEntryKeepsTakenSessions() throws Exception {
        JsonNode cls = newClass();
        LocalDate date = pastWeekday(1);
        JsonNode e = send(post("/api/admin/timetable"),
                entry(cls, date.getDayOfWeek(), 5, "15:00", "16:00", "AOA", "THEORY", null, newTeacher()), 201);
        long sessionId = sessions(cls, date).get(0).get("id").asLong();
        var session = sessionRepository.findById(sessionId).orElseThrow();
        session.setStatus(SessionStatus.TAKEN);
        sessionRepository.save(session);

        send(delete("/api/admin/timetable/" + e.get("id").asLong()), "", 204);

        JsonNode list = sessions(cls, date);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("status").asText()).isEqualTo("TAKEN");
        assertThat(list.get(0).get("extra").asBoolean()).isTrue();   // no longer linked to the timetable
    }

    @Test
    void teacherCanViewADayButNotEditTheTimetable() throws Exception {
        MockHttpSession teacher = login("anita.deshmukh@college.edu", DataSeeder.TEACHER_PASSWORD);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/classes/1/day?date=" + pastWeekday(1)).session(teacher))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/classes/1/timetable").session(teacher))
                .andExpect(status().isForbidden());
    }
}
