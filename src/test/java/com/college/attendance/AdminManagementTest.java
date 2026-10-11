package com.college.attendance;

import com.college.attendance.config.DataSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2: admin management of classes, batches, subjects, teachers and students,
 * including the Excel / CSV upload. Every test creates its own class so tests do not interfere.
 */
class AdminManagementTest extends ApiTestSupport {

    @Autowired
    private ObjectMapper json;

    private MockHttpSession admin;

    @BeforeEach
    void logIn() throws Exception {
        admin = login("admin@college.edu", DataSeeder.ADMIN_PASSWORD);
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode send(MockHttpServletRequestBuilder request, Object body, int expectedStatus) throws Exception {
        String response = mvc.perform(withCsrf(request).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return response.isEmpty() ? null : json.readTree(response);
    }

    private JsonNode newClass() throws Exception {
        String name = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        return send(post("/api/admin/classes"),
                java.util.Map.of("name", name, "batchNames", java.util.List.of("B1", "B2", " B2 ", "")), 201);
    }

    private long batchId(JsonNode cls, String name) {
        for (JsonNode b : cls.get("batches")) {
            if (b.get("name").asText().equals(name)) return b.get("id").asLong();
        }
        throw new AssertionError("no batch " + name);
    }

    private JsonNode preview(long classId, MockMultipartFile file) throws Exception {
        String response = mvc.perform(withCsrf(multipart("/api/admin/classes/" + classId + "/students/import/preview")
                        .file(file)).session(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(response);
    }

    // ---------------------------------------------------------------- classes and batches

    @Test
    void createClassWithBatchesSkipsRepeatsAndBlanks() throws Exception {
        JsonNode cls = newClass();
        assertThat(cls.get("batches")).hasSize(2);

        JsonNode duplicate = send(post("/api/admin/classes"),
                java.util.Map.of("name", cls.get("name").asText().toLowerCase()), 400);
        assertThat(duplicate.get("message").asText()).contains("already exists");

        JsonNode blank = send(post("/api/admin/classes"), java.util.Map.of("name", " "), 400);
        assertThat(blank.get("fieldErrors").get("name").asText()).isEqualTo("Enter a class name.");
    }

    @Test
    void batchWithStudentsCannotBeDeleted() throws Exception {
        JsonNode cls = newClass();
        long classId = cls.get("id").asLong();
        long b1 = batchId(cls, "B1");
        send(post("/api/admin/classes/" + classId + "/students"),
                java.util.Map.of("rollNo", "1", "name", "Asha", "batchId", b1), 201);

        JsonNode error = send(delete("/api/admin/batches/" + b1), "", 400);
        assertThat(error.get("message").asText()).contains("has 1 students");

        // an empty batch can be deleted
        send(delete("/api/admin/batches/" + batchId(cls, "B2")), "", 204);
    }

    @Test
    void seededClassCannotBeDeleted() throws Exception {
        long seCoA = 1;
        JsonNode error = send(delete("/api/admin/classes/" + seCoA), "", 400);
        assertThat(error.get("message").asText()).contains("Remove the students");
    }

    // ---------------------------------------------------------------- students

    @Test
    void addEditMoveAndRemoveStudent() throws Exception {
        JsonNode cls = newClass();
        long classId = cls.get("id").asLong();
        long b1 = batchId(cls, "B1"), b2 = batchId(cls, "B2");

        JsonNode s = send(post("/api/admin/classes/" + classId + "/students"),
                java.util.Map.of("rollNo", " 7 ", "name", "Ravi   Kumar", "batchId", b1), 201);
        assertThat(s.get("rollNo").asText()).isEqualTo("7");
        assertThat(s.get("name").asText()).isEqualTo("Ravi Kumar");

        JsonNode dup = send(post("/api/admin/classes/" + classId + "/students"),
                java.util.Map.of("rollNo", "7", "name", "Someone", "batchId", b1), 400);
        assertThat(dup.get("message").asText()).contains("already used by Ravi Kumar");

        JsonNode moved = send(put("/api/admin/students/" + s.get("id").asLong()),
                java.util.Map.of("rollNo", "7", "name", "Ravi Kumar", "batchId", b2), 200);
        assertThat(moved.get("batchName").asText()).isEqualTo("B2");

        // a batch of another class is refused
        send(put("/api/admin/students/" + s.get("id").asLong()),
                java.util.Map.of("rollNo", "7", "name", "Ravi Kumar", "batchId", 1), 400);

        send(delete("/api/admin/students/" + s.get("id").asLong()), "", 204);
        mvc.perform(get("/api/admin/classes/" + classId + "/students").session(admin))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void studentsAreSortedByRollNumberNumerically() throws Exception {
        JsonNode cls = newClass();
        long classId = cls.get("id").asLong();
        long b1 = batchId(cls, "B1");
        for (String roll : new String[]{"10", "2", "1"}) {
            send(post("/api/admin/classes/" + classId + "/students"),
                    java.util.Map.of("rollNo", roll, "name", "S" + roll, "batchId", b1), 201);
        }
        mvc.perform(get("/api/admin/classes/" + classId + "/students").session(admin))
                .andExpect(jsonPath("$[0].rollNo").value("1"))
                .andExpect(jsonPath("$[1].rollNo").value("2"))
                .andExpect(jsonPath("$[2].rollNo").value("10"));
    }

    // ---------------------------------------------------------------- upload

    @Test
    void csvPreviewReportsDuplicatesUnknownBatchesAndExistingRolls() throws Exception {
        JsonNode cls = newClass();
        long classId = cls.get("id").asLong();
        send(post("/api/admin/classes/" + classId + "/students"),
                java.util.Map.of("rollNo", "5", "name", "Already Here", "batchId", batchId(cls, "B1")), 201);

        String csv = "﻿Roll No,Name,Batch\n"
                + "1,\"Patil, Asha\",B1\n"
                + "2,Rahul,b2\n"
                + "2,Rahul Again,B1\n"
                + "\n"
                + "3,Meena,B9\n"
                + "5,Duplicate Of Existing,B1\n"
                + ",No Roll,B1\n";
        JsonNode p = preview(classId, new MockMultipartFile("file", "students.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(p.get("fileErrors")).isEmpty();
        assertThat(p.get("rows")).hasSize(6);           // the blank line is skipped
        assertThat(p.get("validCount").asInt()).isEqualTo(1);
        assertThat(p.get("errorCount").asInt()).isEqualTo(5);

        JsonNode rows = p.get("rows");
        assertThat(rows.get(0).get("name").asText()).isEqualTo("Patil, Asha");
        assertThat(rows.get(1).get("errors").get(0).asText()).contains("more than once").contains("rows 3, 4");
        assertThat(rows.get(3).get("rowNo").asInt()).isEqualTo(6);
        assertThat(rows.get(3).get("errors").get(0).asText()).contains("Unknown batch B9").contains("B1, B2");
        assertThat(rows.get(4).get("errors").get(0).asText()).contains("already in");
        assertThat(rows.get(5).get("errors").get(0).asText()).isEqualTo("Roll No is missing.");

        // Saving rows with errors is refused and nothing is stored
        String body = "{\"rows\":[{\"rollNo\":\"1\",\"name\":\"A\",\"batch\":\"B1\"},{\"rollNo\":\"1\",\"name\":\"B\",\"batch\":\"B1\"}]}";
        mvc.perform(withCsrf(post("/api/admin/classes/" + classId + "/students/import")).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCount").value(2));
        mvc.perform(get("/api/admin/classes/" + classId).session(admin))
                .andExpect(jsonPath("$.studentCount").value(1));
    }

    @Test
    void excelUploadPreviewAndSave() throws Exception {
        JsonNode cls = newClass();
        long classId = cls.get("id").asLong();

        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Batch");      // columns in a different order
            header.createCell(1).setCellValue("Roll No.");
            header.createCell(2).setCellValue("Student Name");
            for (int i = 1; i <= 3; i++) {
                Row r = sheet.createRow(i);
                r.createCell(0).setCellValue(i == 3 ? "B2" : "B1");
                r.createCell(1).setCellValue(i);              // numeric cell: must read as "1", not "1.0"
                r.createCell(2).setCellValue("Student " + i);
            }
            wb.write(out);
            xlsx = out.toByteArray();
        }
        JsonNode p = preview(classId, new MockMultipartFile("file", "list.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx));
        assertThat(p.get("errorCount").asInt()).isZero();
        assertThat(p.get("rows").get(0).get("rollNo").asText()).isEqualTo("1");

        send(post("/api/admin/classes/" + classId + "/students/import"), java.util.Map.of("rows", p.get("rows")), 200);
        mvc.perform(get("/api/admin/classes/" + classId).session(admin))
                .andExpect(jsonPath("$.studentCount").value(3))
                .andExpect(jsonPath("$.batches[1].studentCount").value(1));
    }

    @Test
    void wrongFileTypeAndMissingHeadingsAreReported() throws Exception {
        long classId = newClass().get("id").asLong();

        JsonNode txt = preview(classId, new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes()));
        assertThat(txt.get("fileErrors").get(0).asText()).contains("Excel");

        JsonNode noHeader = preview(classId, new MockMultipartFile("file", "a.csv", "text/csv",
                "Roll,Student\n1,A\n".getBytes()));
        assertThat(noHeader.get("fileErrors").get(0).asText()).contains("Roll No, Name and Batch");
    }

    @Test
    void templateDownloads() throws Exception {
        byte[] file = mvc.perform(get("/api/admin/students/template.xlsx").session(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(file).startsWith(new byte[]{'P', 'K'});   // xlsx files are zip archives
    }

    // ---------------------------------------------------------------- subjects and teachers

    @Test
    void subjectNeedsTheoryOrLabAndUsedSubjectCannotBeDeleted() throws Exception {
        JsonNode error = send(post("/api/admin/subjects"),
                java.util.Map.of("code", "X1", "name", "Nothing", "hasTheory", false, "hasLab", false), 400);
        assertThat(error.get("fieldErrors").get("theoryOrLab").asText()).isEqualTo("Tick theory, lab or both.");

        JsonNode s = send(post("/api/admin/subjects"),
                java.util.Map.of("code", "OS-" + UUID.randomUUID().toString().substring(0, 4), "name", "Operating Systems",
                        "hasTheory", true, "hasLab", true), 201);
        send(delete("/api/admin/subjects/" + s.get("id").asLong()), "", 204);

        long aoa = 1;   // AOA is in the seeded timetable
        JsonNode inUse = send(delete("/api/admin/subjects/" + aoa), "", 400);
        assertThat(inUse.get("message").asText()).contains("is used");
    }

    @Test
    void newTeacherCanLogInAndPasswordChangeWorks() throws Exception {
        String email = "new." + UUID.randomUUID().toString().substring(0, 6) + "@college.edu";
        JsonNode t = send(post("/api/admin/teachers"),
                java.util.Map.of("name", "New Teacher", "email", email, "password", "secret1"), 201);

        send(post("/api/admin/teachers"),
                java.util.Map.of("name", "Copy", "email", email.toUpperCase(), "password", "secret1"), 400);
        send(post("/api/admin/teachers"),
                java.util.Map.of("name", "Short", "email", "short@college.edu", "password", "123"), 400);

        login(email, "secret1");

        // blank password keeps the old one
        send(put("/api/admin/teachers/" + t.get("id").asLong()),
                java.util.Map.of("name", "Renamed", "email", email, "password", ""), 200);
        login(email, "secret1");

        send(put("/api/admin/teachers/" + t.get("id").asLong()),
                java.util.Map.of("name", "Renamed", "email", email, "password", "secret2"), 200);
        login(email, "secret2");

        send(delete("/api/admin/teachers/" + t.get("id").asLong()), "", 204);
    }

    @Test
    void teacherCannotManageAnything() throws Exception {
        MockHttpSession teacher = login("anita.deshmukh@college.edu", DataSeeder.TEACHER_PASSWORD);
        mvc.perform(get("/api/admin/classes").session(teacher)).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/subjects")).session(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"Z\",\"name\":\"Z\",\"hasTheory\":true,\"hasLab\":false}"))
                .andExpect(status().isForbidden());
    }
}
