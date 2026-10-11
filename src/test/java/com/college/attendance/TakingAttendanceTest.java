package com.college.attendance;

import com.college.attendance.config.DataSeeder;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4: taking attendance.
 * Each test builds a small class: B1 has rolls 1, 2, 10; B2 has rolls 3, 4.
 * Today's timetable: slot 1 AOA theory (teacher A), slot 2 Java lab for B1 (teacher B).
 */
class TakingAttendanceTest extends ApiTestSupport {

    @Autowired
    private ObjectMapper json;
    @Autowired
    private AttendanceRecordRepository attendanceRepository;

    private MockHttpSession admin, teacherA, teacherB;
    private long classId, teacherAId, b2StudentId;
    private final Map<String, Long> studentByRoll = new HashMap<>();
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() throws Exception {
        admin = login("admin@college.edu", DataSeeder.ADMIN_PASSWORD);
        Map<String, Long> subjects = new HashMap<>();
        for (JsonNode s : call(admin, get("/api/admin/subjects"), null, 200)) {
            subjects.put(s.get("code").asText(), s.get("id").asLong());
        }

        JsonNode cls = call(admin, post("/api/admin/classes"),
                Map.of("name", "AT-" + UUID.randomUUID().toString().substring(0, 8), "batchNames", List.of("B1", "B2")), 201);
        classId = cls.get("id").asLong();
        long b1 = cls.get("batches").get(0).get("id").asLong();
        long b2 = cls.get("batches").get(1).get("id").asLong();
        for (String[] st : new String[][]{{"10", "Tenth", "B1"}, {"1", "First", "B1"}, {"2", "Second", "B1"},
                {"3", "Third", "B2"}, {"4", "Fourth", "B2"}}) {
            JsonNode created = call(admin, post("/api/admin/classes/" + classId + "/students"),
                    Map.of("rollNo", st[0], "name", st[1], "batchId", st[2].equals("B1") ? b1 : b2), 201);
            studentByRoll.put(st[0], created.get("id").asLong());
        }
        b2StudentId = studentByRoll.get("3");

        String emailA = newTeacherEmail(), emailB = newTeacherEmail();
        teacherAId = call(admin, post("/api/admin/teachers"), Map.of("name", "Teacher A", "email", emailA, "password", "secret1"), 201)
                .get("id").asLong();
        long teacherBId = call(admin, post("/api/admin/teachers"), Map.of("name", "Teacher B", "email", emailB, "password", "secret1"), 201)
                .get("id").asLong();

        call(admin, post("/api/admin/timetable"), entry(1, "09:00", "10:00", subjects.get("AOA"), "THEORY", null, teacherAId), 201);
        call(admin, post("/api/admin/timetable"), entry(2, "10:00", "12:00", subjects.get("Java"), "LAB", b1, teacherBId), 201);

        teacherA = login(emailA, "secret1");
        teacherB = login(emailB, "secret1");
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

    private static String newTeacherEmail() {
        return "at" + UUID.randomUUID().toString().substring(0, 8) + "@college.edu";
    }

    private Map<String, Object> entry(int slot, String start, String end, long subjectId, String type, Long batchId, long teacherId) {
        Map<String, Object> m = new HashMap<>();
        m.put("classId", classId);
        m.put("dayOfWeek", today.getDayOfWeek().name());
        m.put("slotNo", slot);
        m.put("startTime", start);
        m.put("endTime", end);
        m.put("subjectId", subjectId);
        m.put("sessionType", type);
        m.put("batchId", batchId);
        m.put("teacherId", teacherId);
        m.put("room", "R1");
        return m;
    }

    /** Today's session of this class in the given slot (creating the day's sessions if needed). */
    private long sessionInSlot(int slot) throws Exception {
        for (JsonNode s : call(admin, get("/api/classes/" + classId + "/day"), null, 200).get("sessions")) {
            if (s.get("slotNo").asInt() == slot) return s.get("id").asLong();
        }
        throw new AssertionError("no session in slot " + slot);
    }

    private static List<String> rolls(JsonNode sheet) {
        List<String> rolls = new ArrayList<>();
        sheet.get("students").forEach(s -> rolls.add(s.get("rollNo").asText()));
        return rolls;
    }

    private Map<String, Object> absent(String... rolls) {
        return Map.of("absentStudentIds", Arrays.stream(rolls).map(studentByRoll::get).toList());
    }

    // ---------------------------------------------------------------- my sessions today

    @Test
    void teacherSeesOwnSessionsTodayInTimeOrder() throws Exception {
        JsonNode mine = call(teacherA, get("/api/me/sessions"), null, 200);
        List<JsonNode> ofThisClass = new ArrayList<>();
        mine.forEach(s -> { if (s.get("classId").asLong() == classId) ofThisClass.add(s); });
        assertThat(ofThisClass).hasSize(1);
        assertThat(ofThisClass.get(0).get("subjectCode").asText()).isEqualTo("AOA");
        assertThat(ofThisClass.get(0).get("status").asText()).isEqualTo("PENDING");
        mine.forEach(s -> assertThat(s.get("teacherId").asLong()).isEqualTo(teacherAId));
    }

    // ---------------------------------------------------------------- the student list

    @Test
    void theoryListsWholeClassAndLabOnlyItsBatchAllPresentByRollNo() throws Exception {
        JsonNode theory = call(teacherA, get("/api/sessions/" + sessionInSlot(1) + "/attendance"), null, 200);
        assertThat(rolls(theory)).containsExactly("1", "2", "3", "4", "10");
        assertThat(theory.get("saved").asBoolean()).isFalse();
        theory.get("students").forEach(s -> assertThat(s.get("status").asText()).isEqualTo("PRESENT"));

        JsonNode lab = call(teacherB, get("/api/sessions/" + sessionInSlot(2) + "/attendance"), null, 200);
        assertThat(rolls(lab)).containsExactly("1", "2", "10");
    }

    // ---------------------------------------------------------------- saving and editing

    @Test
    void saveMarksTakenAndEditingOverwrites() throws Exception {
        long id = sessionInSlot(1);
        JsonNode saved = call(teacherA, put("/api/sessions/" + id + "/attendance"), absent("2", "4"), 200);
        assertThat(saved.get("saved").asBoolean()).isTrue();
        assertThat(saved.get("session").get("status").asText()).isEqualTo("TAKEN");
        assertThat(saved.get("session").get("presentCount").asInt()).isEqualTo(3);
        assertThat(saved.get("session").get("absentCount").asInt()).isEqualTo(2);

        JsonNode reopened = call(teacherA, get("/api/sessions/" + id + "/attendance"), null, 200);
        Map<String, String> statusByRoll = new HashMap<>();
        reopened.get("students").forEach(s -> statusByRoll.put(s.get("rollNo").asText(), s.get("status").asText()));
        assertThat(statusByRoll).containsEntry("2", "ABSENT").containsEntry("4", "ABSENT").containsEntry("1", "PRESENT");

        // edit: everyone present now; still one record per student
        call(teacherA, put("/api/sessions/" + id + "/attendance"), absent(), 200);
        assertThat(attendanceRepository.findBySessionId(id)).hasSize(5)
                .allMatch(r -> r.getStatus().name().equals("PRESENT"));

        // the day view shows it as done with counts
        JsonNode day = call(teacherA, get("/api/classes/" + classId + "/day"), null, 200);
        JsonNode first = day.get("sessions").get(0);
        assertThat(first.get("status").asText()).isEqualTo("TAKEN");
        assertThat(first.get("presentCount").asInt()).isEqualTo(5);
    }

    @Test
    void absentStudentMustBeInTheList() throws Exception {
        long labId = sessionInSlot(2);   // B1 lab; roll 3 is in B2
        JsonNode error = call(teacherB, put("/api/sessions/" + labId + "/attendance"),
                Map.of("absentStudentIds", List.of(b2StudentId)), 400);
        assertThat(error.get("message").asText()).contains("not in this session's list");
    }

    // ---------------------------------------------------------------- who may mark

    @Test
    void teacherCannotMarkAnotherTeachersSessionButAdminCan() throws Exception {
        long theoryId = sessionInSlot(1);   // teacher A's
        JsonNode denied = call(teacherB, get("/api/sessions/" + theoryId + "/attendance"), null, 403);
        assertThat(denied.get("message").asText()).contains("assigned to Teacher A");
        call(teacherB, put("/api/sessions/" + theoryId + "/attendance"), absent("1"), 403);
        call(teacherB, post("/api/sessions/" + theoryId + "/cancel"), null, 403);

        call(admin, put("/api/sessions/" + theoryId + "/attendance"), absent("1"), 200);
    }

    // ---------------------------------------------------------------- cancel and reopen

    @Test
    void cancelledSessionCannotBeMarkedUntilReopened() throws Exception {
        long id = sessionInSlot(1);
        assertThat(call(teacherA, post("/api/sessions/" + id + "/cancel"), null, 200).get("status").asText())
                .isEqualTo("CANCELLED");
        JsonNode error = call(teacherA, put("/api/sessions/" + id + "/attendance"), absent(), 400);
        assertThat(error.get("message").asText()).contains("Reopen it");

        assertThat(call(teacherA, post("/api/sessions/" + id + "/reopen"), null, 200).get("status").asText())
                .isEqualTo("PENDING");
        call(teacherA, put("/api/sessions/" + id + "/attendance"), absent("1"), 200);

        call(teacherA, post("/api/sessions/" + id + "/cancel"), null, 200);
        assertThat(call(teacherA, post("/api/sessions/" + id + "/reopen"), null, 200).get("status").asText())
                .as("attendance was saved before, so it goes back to TAKEN").isEqualTo("TAKEN");
    }

    // ---------------------------------------------------------------- extra sessions

    @Test
    void teacherAddsMarksAndDeletesAnExtraSession() throws Exception {
        long aoa = call(admin, get("/api/admin/subjects"), null, 200).get(0).get("id").asLong();
        Map<String, Object> extra = new HashMap<>(Map.of("classId", classId, "date", today.toString(), "slotNo", 6,
                "startTime", "16:00", "endTime", "17:00", "subjectId", aoa, "sessionType", "THEORY", "room", "R9"));
        JsonNode created = call(teacherA, post("/api/sessions/extra"), extra, 201);
        assertThat(created.get("extra").asBoolean()).isTrue();
        assertThat(created.get("teacherId").asLong()).isEqualTo(teacherAId);   // always the teacher themself

        call(teacherA, put("/api/sessions/" + created.get("id").asLong() + "/attendance"), absent("3"), 200);

        assertThat(call(teacherA, post("/api/sessions/extra"), extra, 400).get("message").asText())
                .contains("Slot 6");
        extra.put("slotNo", 1);   // slot 1 already has the timetable theory lecture
        call(teacherA, post("/api/sessions/extra"), extra, 400);
        extra.put("slotNo", 7);
        extra.put("date", today.plusDays(1).toString());
        assertThat(call(teacherA, post("/api/sessions/extra"), extra, 400).get("message").asText())
                .contains("future");

        call(teacherA, delete("/api/sessions/" + created.get("id").asLong()), null, 204);
        assertThat(call(teacherA, delete("/api/sessions/" + sessionInSlot(1)), null, 400).get("message").asText())
                .contains("cannot be deleted");
    }

    @Test
    void adminMustChooseTeacherForExtraSession() throws Exception {
        long aoa = call(admin, get("/api/admin/subjects"), null, 200).get(0).get("id").asLong();
        Map<String, Object> extra = new HashMap<>(Map.of("classId", classId, "date", today.toString(), "slotNo", 8,
                "startTime", "16:00", "endTime", "17:00", "subjectId", aoa, "sessionType", "THEORY"));
        call(admin, post("/api/sessions/extra"), extra, 400);
        extra.put("teacherId", teacherAId);
        assertThat(call(admin, post("/api/sessions/extra"), extra, 201).get("teacherId").asLong()).isEqualTo(teacherAId);
    }
}
