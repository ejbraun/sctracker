package com.howl.uwtracker.plugin;

import com.howl.uwtracker.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The "new plugin version available" banner flag ({@code PersonResponse.new_plugin_version_available},
 * from {@link PluginVersionService#isOutdated}). It's driven by the build the plugin last advertised
 * over {@code X-Plugin-Version} — stamped onto {@code people.last_seen_plugin_version} by
 * {@link com.howl.uwtracker.web.MachineKeyAuthenticationService} on every machine-key request —
 * compared against the current manifest version. The fake manifest
 * ({@link FakePluginStorageConfig}) is version {@link FakePluginStorageConfig#FAKE_VERSION} (10), so
 * a heartbeat on {@code 10} clears the banner and {@code 9} / a missing header keeps it.
 */
class PluginIntegrationTest extends AbstractIntegrationTest {

    private static final int CURRENT = FakePluginStorageConfig.FAKE_VERSION;

    /** The plugin's once-per-load heartbeat with the given version header (null = omit it). */
    private ResultActions heartbeat(String machineKey, Integer pluginVersion) throws Exception {
        var request = get("/can-report-run-failure").header("X-Machine-Key", machineKey);
        if (pluginVersion != null) {
            request = request.header("X-Plugin-Version", pluginVersion.toString());
        }
        return mockMvc.perform(request);
    }

    private ResultActions me(MockHttpSession session) throws Exception {
        return mockMvc.perform(get("/api/account/me").session(session));
    }

    @Test
    void showsTheBannerWhenThePluginHasNeverAuthenticated() throws Exception {
        MockHttpSession session = signup("never-seen", "password123");

        me(session).andExpect(status().isOk())
                .andExpect(jsonPath("$.new_plugin_version_available").value(true));
    }

    @Test
    void aCurrentVersionHeartbeatClearsTheBanner() throws Exception {
        MockHttpSession session = signup("fresh-plugin", "password123");
        String key = generateMachineKey(session, "GWToolboxdll");

        me(session).andExpect(jsonPath("$.new_plugin_version_available").value(true));

        heartbeat(key, CURRENT).andExpect(status().isOk());

        me(session).andExpect(status().isOk())
                .andExpect(jsonPath("$.new_plugin_version_available").value(false));
    }

    @Test
    void anOutdatedVersionHeartbeatKeepsTheBanner() throws Exception {
        MockHttpSession session = signup("stale-plugin", "password123");
        String key = generateMachineKey(session, "GWToolboxdll");

        // The heartbeat itself 426s on an old version, but the sighting is stamped before the check.
        heartbeat(key, CURRENT - 1).andExpect(status().isUpgradeRequired());

        me(session).andExpect(status().isOk())
                .andExpect(jsonPath("$.new_plugin_version_available").value(true));
    }

    @Test
    void aClientTooOldToSendAVersionHeaderKeepsTheBanner() throws Exception {
        MockHttpSession session = signup("ancient-plugin", "password123");
        String key = generateMachineKey(session, "GWToolboxdll");

        heartbeat(key, null).andExpect(status().isUpgradeRequired());

        me(session).andExpect(status().isOk())
                .andExpect(jsonPath("$.new_plugin_version_available").value(true));
    }
}
