package com.college.attendance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /.well-known/assetlinks.json as served on Render, with the fingerprint of the key that signed
 * AttendanceTracker.apk. Android only opens the app full screen (no address bar) if this matches.
 */
@SpringBootTest(properties = {
        "app.android.package-name=je.qd.attandtrackinger.twa",
        "app.android.cert-sha256=" + AssetLinksConfiguredTest.FINGERPRINT})
@AutoConfigureMockMvc
class AssetLinksConfiguredTest {

    static final String FINGERPRINT =
            "43:2F:20:B6:F2:EE:B3:94:5C:90:FA:61:7B:F8:93:3C:EC:8F:07:84:C4:33:FF:63:B2:BA:E8:6B:5F:E1:45:DB";

    @Autowired
    private MockMvc mvc;

    @Test
    void servesTheStatementForTheSignedApp() throws Exception {
        mvc.perform(get("/.well-known/assetlinks.json"))     // no session, no login
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))   // Android does not follow redirects
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].relation", contains("delegate_permission/common.handle_all_urls")))
                .andExpect(jsonPath("$[0].target.namespace").value("android_app"))
                .andExpect(jsonPath("$[0].target.package_name").value("je.qd.attandtrackinger.twa"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints", contains(FINGERPRINT)));
    }

    /**
     * The tests use their own application.properties, so check the real one: on Render the
     * values come from the ANDROID_CERT_SHA256 and ANDROID_PACKAGE_NAME environment variables.
     */
    @Test
    void mainSettingsReadTheRenderEnvironmentVariables() throws Exception {
        Properties main = new Properties();
        try (Reader in = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) {
            main.load(in);
        }
        assertThat(main.getProperty("app.android.cert-sha256")).isEqualTo("${ANDROID_CERT_SHA256:}");
        assertThat(main.getProperty("app.android.package-name"))
                .isEqualTo("${ANDROID_PACKAGE_NAME:je.qd.attandtrackinger.twa}");
    }
}
