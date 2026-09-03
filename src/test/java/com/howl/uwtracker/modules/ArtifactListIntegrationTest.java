package com.howl.uwtracker.modules;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.plugin.FakePluginStorageConfig;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /artifacts} — public, top-level, lists every enabled module with its version. */
class ArtifactListIntegrationTest extends AbstractIntegrationTest {

    @Test
    void listsEnabledModulesUnauthenticated() throws Exception {
        seedModule("sctracker", true);
        long gatedId = seedModule("pp-vanquish", false);
        long disabledId = seedModule("pp-retired", false);
        jdbcTemplate.update("UPDATE modules SET enabled = 0 WHERE id = ?", disabledId);

        mockMvc.perform(get("/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifacts.length()").value(2))
                .andExpect(jsonPath("$.artifacts[?(@.key == 'pp-retired')]").isEmpty())
                .andExpect(jsonPath("$.artifacts[?(@.key == 'sctracker')].is_public").value(true))
                .andExpect(jsonPath("$.artifacts[?(@.key == 'sctracker')].download_url").value("/SCTracker.dll"))
                .andExpect(jsonPath("$.artifacts[?(@.key == 'sctracker')].version")
                        .value(FakePluginStorageConfig.FAKE_VERSION))
                .andExpect(jsonPath("$.artifacts[?(@.key == 'pp-vanquish')].is_public").value(false))
                .andExpect(jsonPath("$.artifacts[?(@.key == 'pp-vanquish')].download_url")
                        .value("/modules/pp-vanquish/download"))
                .andExpect(jsonPath("$.artifacts[?(@.key == 'pp-vanquish')].version")
                        .value(FakePluginStorageConfig.FAKE_VERSION));
    }

    @Test
    void emptyRegistryIsAnEmptyList() throws Exception {
        mockMvc.perform(get("/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifacts.length()").value(0));
    }
}
