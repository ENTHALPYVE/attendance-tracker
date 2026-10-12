package com.college.attendance.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Digital Asset Links for the Android app (a Trusted Web Activity, see android/README.md).
 * Android downloads this file to check that the app and this website belong together; only
 * then does the app open the site full screen, without a browser address bar.
 *
 * The values come from settings (environment variables on Render):
 *   ANDROID_PACKAGE_NAME  - the app's package name
 *   ANDROID_CERT_SHA256   - SHA-256 fingerprint(s) of the signing key, comma separated.
 *                           A fingerprint is public information, not a secret.
 * When no fingerprint is set the list is empty, which is valid and simply means "no app".
 */
@RestController
public class WellKnownController {

    private static final Logger log = LoggerFactory.getLogger(WellKnownController.class);
    private static final Pattern FINGERPRINT = Pattern.compile("([0-9A-F]{2}:){31}[0-9A-F]{2}");

    private final List<Map<String, Object>> statements;

    public WellKnownController(@Value("${app.android.package-name:}") String packageName,
                               @Value("${app.android.cert-sha256:}") String fingerprints) {
        List<String> valid = Arrays.stream(fingerprints.split(","))
                .map(f -> f.trim().toUpperCase())
                .filter(f -> !f.isEmpty())
                .filter(f -> {
                    boolean ok = FINGERPRINT.matcher(f).matches();
                    if (!ok) log.warn("Ignoring ANDROID_CERT_SHA256 value that is not a SHA-256 fingerprint: {}", f);
                    return ok;
                })
                .toList();
        this.statements = packageName.isBlank() || valid.isEmpty() ? List.of() : List.of(Map.of(
                "relation", List.of("delegate_permission/common.handle_all_urls"),
                "target", Map.of(
                        "namespace", "android_app",
                        "package_name", packageName.trim(),
                        "sha256_cert_fingerprints", valid)));
    }

    @GetMapping(value = "/.well-known/assetlinks.json", produces = "application/json")
    public List<Map<String, Object>> assetLinks() {
        return statements;
    }
}
