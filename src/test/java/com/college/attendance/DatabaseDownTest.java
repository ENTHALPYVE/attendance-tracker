package com.college.attendance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The database is powered off (nothing listens on port 1): the server must still start
 * and serve the website, while everything that needs data answers 503.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/attendance?connectTimeout=2000",
        "spring.datasource.hikari.connection-timeout=3000",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false"
})
class DatabaseDownTest extends ApiTestSupport {

    @Test
    void websiteStillOpens() throws Exception {
        mvc.perform(get("/login.html")).andExpect(status().isOk());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    @Test
    void healthReportsTheDatabaseDown() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void loginSaysDatabaseDownNotWrongPassword() throws Exception {
        mvc.perform(withCsrf(post("/api/auth/login"))
                        .param("email", "admin@college.edu").param("password", "admin123"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("database")));
    }
}
