package com.college.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The web app manifest's icons: Chrome, the installed PWA and the Android app (Bubblewrap reads
 * the same files) need real PNGs of the stated sizes, served without login.
 */
class ManifestIconsTest extends ApiTestSupport {

    @Autowired
    private ObjectMapper json;

    @Test
    void everyManifestIconIsAPngOfTheStatedSizeAndIsPublic() throws Exception {
        JsonNode manifest;
        try (InputStream in = new ClassPathResource("static/manifest.json").getInputStream()) {
            manifest = json.readTree(in);
        }
        Set<String> kinds = new HashSet<>();
        for (JsonNode icon : manifest.get("icons")) {
            String src = icon.get("src").asText();
            String sizes = icon.get("sizes").asText();
            String purpose = icon.get("purpose").asText();
            assertThat(icon.get("type").asText()).isEqualTo("image/png");

            BufferedImage image;
            try (InputStream in = new ClassPathResource("static" + src).getInputStream()) {
                image = ImageIO.read(in);
            }
            assertThat(image).as(src + " is a readable PNG").isNotNull();
            assertThat(image.getWidth() + "x" + image.getHeight()).as(src).isEqualTo(sizes);
            if (purpose.equals("maskable")) {
                // maskable icons must fill the whole square: no transparent corners
                assertThat(image.getRGB(0, 0) >>> 24).as(src + " corner alpha").isEqualTo(255);
            }
            if (purpose.equals("monochrome")) {
                assertThat(image.getColorModel().hasAlpha()).as(src + " has transparency").isTrue();
                assertThat(image.getRGB(0, 0) >>> 24).as(src + " transparent background").isZero();
            }

            mvc.perform(get(src)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
            kinds.add(purpose + " " + sizes);
        }
        assertThat(kinds).contains("any 192x192", "any 512x512", "maskable 192x192", "maskable 512x512",
                "monochrome 512x512");
    }

    @Test
    void manifestIsServedPublicly() throws Exception {
        mvc.perform(get("/manifest.json")).andExpect(status().isOk());
    }
}
