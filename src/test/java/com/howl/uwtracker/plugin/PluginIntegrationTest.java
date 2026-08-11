package com.howl.uwtracker.plugin;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.domain.Person;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The "new plugin version available" banner flag against real MySQL. PluginDllVersionInitializer
 * runs once at real app startup (well before any test method here), so by the time these run,
 * plugin_dll_version already has a row whose detected_at is "around when this test JVM booted" —
 * these tests only need to control each person's last_plugin_download_at relative to that.
 */
class PluginIntegrationTest extends AbstractIntegrationTest {

    @Test
    void neverDownloadedShowsTheBannerSinceThereIsNoTimestampToCompare() throws Exception {
        MockHttpSession session = signup("neverdownloaded", "password123");

        mockMvc.perform(get("/api/account/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.new_plugin_version_available").value(true));
    }

    @Test
    void downloadingRecordsTimestampSoMeNoLongerFlagsAnUpdate() throws Exception {
        MockHttpSession session = signup("freshdownloader", "password123");
        // Backdate to before app startup, to exercise the "stale download" path specifically
        // (distinct from the "never downloaded" path, which also starts true but for a different
        // reason — see neverDownloadedShowsTheBannerSinceThereIsNoTimestampToCompare).
        backdateLastDownload("freshdownloader", Instant.EPOCH);
        mockMvc.perform(get("/api/account/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.new_plugin_version_available").value(true));

        mockMvc.perform(post("/api/plugin/download").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/account/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.new_plugin_version_available").value(false));
    }

    @Test
    void meFlagsAnUpdateWhenLastDownloadPredatesTheDetectedDllVersion() throws Exception {
        MockHttpSession session = signup("staleplayer", "password123");
        backdateLastDownload("staleplayer", Instant.EPOCH);

        mockMvc.perform(get("/api/account/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.new_plugin_version_available").value(true));
    }

    @Test
    void downloadEndpointRequiresAnActiveSession() throws Exception {
        mockMvc.perform(post("/api/plugin/download"))
                .andExpect(status().isUnauthorized());
    }

    private void backdateLastDownload(String username, Instant when) {
        Person person = personRepository.findByUsername(username).orElseThrow();
        person.setLastPluginDownloadAt(when);
        personRepository.save(person);
    }
}
