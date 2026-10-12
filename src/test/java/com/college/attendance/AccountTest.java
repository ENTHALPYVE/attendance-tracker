package com.college.attendance;

import com.college.attendance.model.Role;
import com.college.attendance.model.User;
import com.college.attendance.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Changing one's own password (Phase 3). Each test uses its own new teacher account. */
class AccountTest extends ApiTestSupport {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper json;

    private String email;

    @BeforeEach
    void createTeacher() {
        email = "acc" + UUID.randomUUID().toString().substring(0, 8) + "@college.edu";
        userRepository.save(new User("Account Test", email, passwordEncoder.encode("oldpass1"), Role.TEACHER));
    }

    private ResultActions change(MockHttpSession session, String current, String next) throws Exception {
        return mvc.perform(withCsrf(post("/api/auth/password")).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", current, "newPassword", next))));
    }

    @Test
    void userCanChangeTheirOwnPassword() throws Exception {
        MockHttpSession session = login(email, "oldpass1");

        change(session, "oldpass1", "new-password-9").andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your password has been changed."));

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());   // still logged in
        login(email, "new-password-9");
        mvc.perform(withCsrf(post("/api/auth/login")).param("email", email).param("password", "oldpass1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCurrentPasswordIsRejectedAndNothingChanges() throws Exception {
        MockHttpSession session = login(email, "oldpass1");

        change(session, "not-my-password", "new-password-9").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your current password is not correct."));
        login(email, "oldpass1");
    }

    @Test
    void newPasswordRules() throws Exception {
        MockHttpSession session = login(email, "oldpass1");

        change(session, "oldpass1", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
        change(session, "oldpass1", "oldpass1").andExpect(status().isBadRequest());     // same as before (8 chars)
        change(session, "oldpass1", email).andExpect(status().isBadRequest());          // the email address
        change(session, "oldpass1", "é".repeat(40)).andExpect(status().isBadRequest()); // 80 bytes > BCrypt limit
        login(email, "oldpass1");
    }

    @Test
    void tooManyWrongCurrentPasswordsLockForAWhile() throws Exception {
        MockHttpSession session = login(email, "oldpass1");
        for (int i = 0; i < 5; i++) {
            change(session, "wrong" + i, "new-password-9").andExpect(status().isBadRequest());
        }
        change(session, "oldpass1", "new-password-9").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Too many wrong passwords")));
        login(email, "oldpass1");   // the password did not change
    }

    @Test
    void needsLoginAndCsrf() throws Exception {
        mvc.perform(withCsrf(post("/api/auth/password")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"x\",\"newPassword\":\"yyyyyyyy\"}"))
                .andExpect(status().isUnauthorized());

        MockHttpSession session = login(email, "oldpass1");
        mvc.perform(post("/api/auth/password").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldpass1\",\"newPassword\":\"new-password-9\"}"))
                .andExpect(status().isForbidden());                                      // no CSRF token
        login(email, "oldpass1");
    }
}
