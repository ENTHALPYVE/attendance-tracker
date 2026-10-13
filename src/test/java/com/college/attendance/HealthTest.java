package com.college.attendance;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /health for the uptime monitor. */
class HealthTest extends ApiTestSupport {

    @Test
    void publicAndChecksTheDatabase() throws Exception {
        mvc.perform(get("/health"))                                // no login needed: the monitor calls it
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"ok\"}"));
    }
}
