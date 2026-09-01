package com.howl.uwtracker.loserboards;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.plugin.FakePluginStorageConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reworked "Players On An Outdated Plugin" Loserboards widget. Every machine-key request stamps
 * {@code people.last_plugin_seen_at} / {@code last_seen_plugin_version}
 * (MachineKeyAuthenticationService); the widget lists people seen within the window whose version is
 * below the current one. The fake manifest is version {@link FakePluginStorageConfig#FAKE_VERSION}
 * (10), so {@code X-Plugin-Version: 9} / missing = outdated, {@code 10} = current.
 */
class OutdatedPluginIntegrationTest extends AbstractIntegrationTest {

    private static final int CURRENT = FakePluginStorageConfig.FAKE_VERSION;

    /** Calls the plugin's once-per-load heartbeat with the given version header (null = omit it). */
    private ResultActions canReportRunFailure(String machineKey, Integer pluginVersion) throws Exception {
        var request = get("/can-report-run-failure").header("X-Machine-Key", machineKey);
        if (pluginVersion != null) {
            request = request.header("X-Plugin-Version", pluginVersion.toString());
        }
        return mockMvc.perform(request);
    }

    private ResultActions fetchWidget(MockHttpSession session, String query) throws Exception {
        return mockMvc.perform(get("/api/loserboards/outdated-plugins" + query).session(session));
    }

    @Test
    void listsAnActiveUserWhoseHeartbeatReportedAnOldVersion() throws Exception {
        MockHttpSession session = signup("stale-ranger", "password123");
        String key = generateMachineKey(session, "GWToolboxdll");

        // Outdated -> the heartbeat itself 426s, but the stamp lands before the version check.
        canReportRunFailure(key, CURRENT - 1).andExpect(status().isUpgradeRequired());

        fetchWidget(session, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].user").value("stale-ranger"))
                .andExpect(jsonPath("$[0].plugin_version").value(CURRENT - 1))
                .andExpect(jsonPath("$[0].last_seen").isNotEmpty());
    }

    @Test
    void doesNotListAUserOnTheCurrentVersion() throws Exception {
        MockHttpSession session = signup("fresh-derv", "password123");
        String key = generateMachineKey(session, "GWToolboxdll");

        canReportRunFailure(key, CURRENT).andExpect(status().isOk());

        fetchWidget(session, "").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        // The stamp still happened on the successful auth — just not as an "outdated" row.
        Long personId = personRepository.findByUsername("fresh-derv").orElseThrow().getId();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_seen_plugin_version FROM people WHERE id = ?", Integer.class, personId))
                .isEqualTo(CURRENT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_plugin_seen_at IS NOT NULL FROM people WHERE id = ?", Boolean.class, personId))
                .isTrue();
    }

    @Test
    void listsAClientTooOldToSendAVersionHeaderAsUnknown() throws Exception {
        MockHttpSession session = signup("ancient-client", "password123");
        String key = generateMachineKey(session, "GWToolboxdll");

        canReportRunFailure(key, null).andExpect(status().isUpgradeRequired());

        fetchWidget(session, "")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].user").value("ancient-client"))
                .andExpect(jsonPath("$[0].plugin_version").value(nullValue()));
    }

    @Test
    void doesNotListAUserWhosePluginHasNeverAuthenticated() throws Exception {
        MockHttpSession session = signup("website-only", "password123");
        generateMachineKey(session, "GWToolboxdll"); // key exists, but no plugin has ever used it

        fetchWidget(session, "").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void respectsTheFromWindow() throws Exception {
        MockHttpSession session = signup("seen-earlier", "password123");
        String key = generateMachineKey(session, "GWToolboxdll");
        canReportRunFailure(key, CURRENT - 1).andExpect(status().isUpgradeRequired());

        // A window that starts in the future excludes the just-recorded sighting.
        fetchWidget(session, "?from=2999-01-01T00:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void theUploadPathStampsLastSeenToo() throws Exception {
        MockHttpSession session = signup("uploader", "password123");
        String key = generateMachineKey(session, "GWToolboxdll");

        // authenticateForUpload runs before the body is parsed — a null body still 426s on an old
        // version, and still stamps.
        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", String.valueOf(CURRENT - 1))
                        .contentType("application/json")
                        .content("{\"party\":null,\"objective\":null}"))
                .andExpect(status().isUpgradeRequired());

        fetchWidget(session, "")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].user").value("uploader"))
                .andExpect(jsonPath("$[0].plugin_version").value(CURRENT - 1));
    }
}
