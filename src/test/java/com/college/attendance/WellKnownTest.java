package com.college.attendance;

import com.college.attendance.controller.WellKnownController;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /.well-known/assetlinks.json for the Android app. */
class WellKnownTest extends ApiTestSupport {

    private static final String FP = "14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5";

    @Test
    void publicAndEmptyUntilAFingerprintIsConfigured() throws Exception {
        mvc.perform(get("/.well-known/assetlinks.json"))          // no login needed: Android fetches it
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().json("[]"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsThePackageAndValidFingerprints() {
        List<Map<String, Object>> links = new WellKnownController("je.qd.attandtrackinger.twa",
                FP.toLowerCase() + ", not-a-fingerprint").assetLinks();

        assertThat(links).hasSize(1);
        Map<String, Object> target = (Map<String, Object>) links.get(0).get("target");
        assertThat(target.get("package_name")).isEqualTo("je.qd.attandtrackinger.twa");
        assertThat(target.get("sha256_cert_fingerprints")).isEqualTo(List.of(FP));   // upper-cased, bad one dropped
        assertThat(links.get(0).get("relation")).isEqualTo(List.of("delegate_permission/common.handle_all_urls"));
    }

    @Test
    void nothingWithoutAPackageName() {
        assertThat(new WellKnownController("", FP).assetLinks()).isEmpty();
    }
}
