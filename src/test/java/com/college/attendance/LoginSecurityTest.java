package com.college.attendance;

import com.college.attendance.model.Role;
import com.college.attendance.model.User;
import com.college.attendance.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 5: limits on password guessing and the security headers. */
class LoginSecurityTest extends ApiTestSupport {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String newTeacher() {
        String email = "sec" + UUID.randomUUID().toString().substring(0, 8) + "@college.edu";
        userRepository.save(new User("Security Test", email, passwordEncoder.encode("rightpass1"), Role.TEACHER));
        return email;
    }

    private ResultActions attempt(String email, String password, String ip) throws Exception {
        return mvc.perform(withCsrf(post("/api/auth/login")).param("email", email).param("password", password)
                .with(request -> { request.setRemoteAddr(ip); return request; }));
    }

    @Test
    void fiveWrongPasswordsLockThatEmailFromThatAddress() throws Exception {
        String email = newTeacher();
        for (int i = 0; i < 5; i++) {
            attempt(email, "wrong" + i, "10.1.0.1").andExpect(status().isUnauthorized());
        }
        // even the right password must wait now
        attempt(email, "rightpass1", "10.1.0.1").andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("8[5-9][0-9]|900")))
                .andExpect(jsonPath("$.message").value("Too many failed login attempts. Try again in 15 minutes."));
        // the same person from another network, and other users, are not affected
        attempt(email, "rightpass1", "10.1.0.2").andExpect(status().isOk());
        attempt(newTeacher(), "rightpass1", "10.1.0.1").andExpect(status().isOk());
    }

    @Test
    void unknownEmailsAreLimitedTheSameWay() throws Exception {
        String email = "nobody" + UUID.randomUUID().toString().substring(0, 8) + "@college.edu";
        for (int i = 0; i < 5; i++) {
            attempt(email, "guess" + i, "10.2.0.1").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Incorrect email or password."));
        }
        attempt(email, "guess", "10.2.0.1").andExpect(status().isTooManyRequests());
    }

    @Test
    void aCorrectPasswordResetsTheCount() throws Exception {
        String email = newTeacher();
        for (int i = 0; i < 4; i++) attempt(email, "wrong" + i, "10.3.0.1").andExpect(status().isUnauthorized());
        attempt(email, "rightpass1", "10.3.0.1").andExpect(status().isOk());
        for (int i = 0; i < 4; i++) attempt(email, "wrong" + i, "10.3.0.1").andExpect(status().isUnauthorized());
        attempt(email, "rightpass1", "10.3.0.1").andExpect(status().isOk());
    }

    @Test
    void changingAddressDoesNotGetAroundTheLimit() throws Exception {
        String email = newTeacher();
        for (int i = 1; i <= 20; i++) {
            attempt(email, "wrong", "10.4.0." + i).andExpect(status().isUnauthorized());
        }
        attempt(email, "rightpass1", "10.4.1.1").andExpect(status().isTooManyRequests());
    }

    @Test
    void securityHeadersAreSent() throws Exception {
        MockHttpServletResponse page = mvc.perform(get("/login.html")).andReturn().getResponse();
        String csp = page.getHeader("Content-Security-Policy");
        assertThat(csp).contains("default-src 'self'", "object-src 'none'", "frame-ancestors 'none'", "base-uri 'self'");
        String scriptSrc = csp.substring(csp.indexOf("script-src"), csp.indexOf(';', csp.indexOf("script-src")));
        assertThat(scriptSrc).doesNotContain("unsafe-inline").doesNotContain("unsafe-eval");
        assertThat(page.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(page.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(page.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(page.getHeader("Permissions-Policy")).contains("camera=()", "geolocation=()");

        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Security-Policy", startsWith("default-src 'self'")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    /** The CSP forbids inline scripts, so a page with one would silently break. */
    @Test
    void pagesHaveNoInlineScriptsOrEventHandlers() throws Exception {
        Pattern inlineScript = Pattern.compile("<script(?![^>]*\\bsrc=)[^>]*>", Pattern.CASE_INSENSITIVE);
        Pattern handler = Pattern.compile("\\son[a-z]+\\s*=\\s*[\"']", Pattern.CASE_INSENSITIVE);
        Pattern cdnWithoutIntegrity = Pattern.compile("<script[^>]*src=\"https://(?![^>]*integrity=)[^>]*>");
        Resource[] pages = new PathMatchingResourcePatternResolver().getResources("classpath:static/**/*.html");
        assertThat(pages).hasSizeGreaterThan(10);
        for (Resource r : pages) {
            String html = r.getContentAsString(StandardCharsets.UTF_8);
            assertThat(inlineScript.matcher(html).find()).as("inline <script> in " + r.getFilename()).isFalse();
            assertThat(handler.matcher(html).find()).as("inline event handler in " + r.getFilename()).isFalse();
            assertThat(cdnWithoutIntegrity.matcher(html).find()).as("CDN script without integrity in " + r.getFilename()).isFalse();
        }
    }
}
