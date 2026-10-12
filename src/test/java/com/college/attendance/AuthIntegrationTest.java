package com.college.attendance;

import com.college.attendance.config.DataSeeder;
import com.college.attendance.repository.ClassSessionRepository;
import com.college.attendance.repository.StudentRepository;
import com.college.attendance.repository.TimetableEntryRepository;
import com.college.attendance.repository.UserRepository;
import com.college.attendance.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks the demo data, login, CSRF and the role rules. */
class AuthIntegrationTest extends ApiTestSupport {

    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TimetableEntryRepository timetableRepository;
    @Autowired
    private ClassSessionRepository sessionRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void demoDataIsLoaded() {
        assertThat(studentRepository.countByCollegeClassId(1L)).isEqualTo(60);   // SE-CO-A
        assertThat(timetableRepository.count()).isGreaterThan(30);
        assertThat(sessionRepository.count()).isGreaterThan(100);
    }

    @Test
    void adminCanLogInAndSeeAdminApi() throws Exception {
        MockHttpSession session = login("admin@college.edu", DataSeeder.ADMIN_PASSWORD);

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.name").value("Dr. Suresh Menon"))
                .andExpect(jsonPath("$.email").value("admin@college.edu"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/admin/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students").value(greaterThanOrEqualTo(60)))
                .andExpect(jsonPath("$.teachers").value(greaterThanOrEqualTo(8)));
    }

    @Test
    void teacherCannotUseAdminApi() throws Exception {
        MockHttpSession session = login("anita.deshmukh@college.edu", DataSeeder.TEACHER_PASSWORD);

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.name").value("Anita Deshmukh"))
                .andExpect(jsonPath("$.email").value("anita.deshmukh@college.edu"));
        mvc.perform(get("/api/admin/summary").session(session))
                .andExpect(status().isForbidden());
    }

    /** /me is identified by the session, not by anything the browser sends. */
    @Test
    void meIgnoresIdentityParametersFromTheBrowser() throws Exception {
        MockHttpSession session = login("anita.deshmukh@college.edu", DataSeeder.TEACHER_PASSWORD);

        mvc.perform(get("/api/auth/me").session(session)
                        .param("role", "ADMIN").param("name", "Hacker").param("id", "1"))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.name").value("Anita Deshmukh"));
        mvc.perform(get("/api/admin/summary").session(session).param("role", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    /** A name changed in the database shows up on the next /me call, without logging in again. */
    @Test
    void meShowsTheCurrentNameFromTheDatabase() throws Exception {
        MockHttpSession session = login("admin@college.edu", DataSeeder.ADMIN_PASSWORD);
        User admin = userRepository.findByEmailIgnoreCase("admin@college.edu").orElseThrow();
        String original = admin.getName();
        try {
            admin.setName("Tanish Maheshwari");
            userRepository.save(admin);
            mvc.perform(get("/api/auth/me").session(session))
                    .andExpect(jsonPath("$.name").value("Tanish Maheshwari"))
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        } finally {
            admin.setName(original);
            userRepository.save(admin);
        }
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(withCsrf(post("/api/auth/login"))
                        .param("email", "admin@college.edu").param("password", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect email or password."));
    }

    @Test
    void apiNeedsLogin() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    /** The CSRF token changes at login; the login response must carry the new one for api.js. */
    @Test
    void loginResponseSendsNewCsrfToken() throws Exception {
        String oldToken = csrfToken();
        java.util.List<String> cookies = mvc.perform(post("/api/auth/login")
                        .cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", oldToken))
                        .header("X-XSRF-TOKEN", oldToken)
                        .param("email", "admin@college.edu").param("password", DataSeeder.ADMIN_PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeaders("Set-Cookie");
        String newCookie = cookies.stream()
                .filter(c -> c.startsWith("XSRF-TOKEN=") && !c.startsWith("XSRF-TOKEN=;"))
                .findFirst().orElseThrow(() -> new AssertionError("no new XSRF-TOKEN in " + cookies));
        assertThat(newCookie).doesNotContain(oldToken);
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .param("email", "admin@college.edu").param("password", DataSeeder.ADMIN_PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = login("admin@college.edu", DataSeeder.ADMIN_PASSWORD);

        mvc.perform(withCsrf(post("/api/auth/logout")).session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }
}
