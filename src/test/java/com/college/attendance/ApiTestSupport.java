package com.college.attendance;

import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for API tests. Starts the whole app on H2 with the demo data.
 *
 * CSRF is handled exactly like the browser does it (cookie + X-XSRF-TOKEN header).
 * Do not use spring-security-test's .with(csrf()) in these tests: it replaces the CSRF
 * repository of the shared application context, after which the real cookie is no longer sent.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mvc;

    /** Loads a page and returns the CSRF token from the XSRF-TOKEN cookie the server sets. */
    protected String csrfToken() throws Exception {
        String setCookie = mvc.perform(get("/login.html"))
                .andReturn().getResponse().getHeader("Set-Cookie");
        if (setCookie == null || !setCookie.startsWith("XSRF-TOKEN=")) {
            throw new IllegalStateException("Server did not send the XSRF-TOKEN cookie");
        }
        return setCookie.substring("XSRF-TOKEN=".length(), setCookie.indexOf(';'));
    }

    /** Adds the CSRF cookie and header to a POST/PUT/DELETE request. */
    protected MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        String token = csrfToken();
        return request.cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token);
    }

    /** Logs in and returns the HTTP session to use for further requests. */
    protected MockHttpSession login(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(withCsrf(post("/api/auth/login"))
                        .param("email", email).param("password", password))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
