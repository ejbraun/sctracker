package com.howl.uwtracker.modules;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.plugin.FakePluginStorageConfig;
import com.howl.uwtracker.web.MachineKeyHasher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /module-entitlements} — key-only auth (no 426 gate), returns public + granted modules. */
class ModuleEntitlementIntegrationTest extends AbstractIntegrationTest {

    private String keyFor(String username) throws Exception {
        MockHttpSession session = signup(username, "password123");
        return generateMachineKey(session, "pp");
    }

    private long personId(String username) {
        return personRepository.findByUsername(username).orElseThrow().getId();
    }

    @Test
    void missingKeyIs401() throws Exception {
        mockMvc.perform(get("/module-entitlements"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidKeyIs401() throws Exception {
        mockMvc.perform(get("/module-entitlements").header("X-Machine-Key", "not-a-real-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedKeyIs401() throws Exception {
        String key = keyFor("revoked");
        jdbcTemplate.update("UPDATE machine_keys SET revoked_at = NOW(6) WHERE key_hash = ?",
                MachineKeyHasher.hash(key));

        mockMvc.perform(get("/module-entitlements").header("X-Machine-Key", key))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validKeyWithNoGrantsSeesPublicModulesOnly() throws Exception {
        seedModule("pp-exe", true);
        seedModule("pp-vanquish", false);
        String key = keyFor("plainuser");

        mockMvc.perform(get("/module-entitlements").header("X-Machine-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.length()").value(1))
                .andExpect(jsonPath("$.modules[0].key").value("pp-exe"))
                .andExpect(jsonPath("$.modules[0].is_public").value(true))
                .andExpect(jsonPath("$.modules[0].download_url").value("/modules/pp-exe/download"))
                .andExpect(jsonPath("$.modules[0].version").value(FakePluginStorageConfig.FAKE_VERSION));
    }

    @Test
    void grantedModuleAppearsThenDisappearsAfterRevoke() throws Exception {
        seedModule("pp-exe", true);
        long gatedId = seedModule("pp-vanquish", false);
        String key = keyFor("granted");
        long personId = personId("granted");

        grantModule(personId, gatedId);
        mockMvc.perform(get("/module-entitlements").header("X-Machine-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.length()").value(2))
                .andExpect(jsonPath("$.modules[?(@.key == 'pp-vanquish')].is_public").value(false));

        jdbcTemplate.update("DELETE FROM person_module_grants WHERE person_id = ? AND module_id = ?", personId, gatedId);
        mockMvc.perform(get("/module-entitlements").header("X-Machine-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.length()").value(1))
                .andExpect(jsonPath("$.modules[?(@.key == 'pp-vanquish')]").isEmpty());
    }

    @Test
    void sctrackerDownloadUrlIsItsDedicatedRoute() throws Exception {
        seedModule("sctracker", true);
        String key = keyFor("scuser");

        mockMvc.perform(get("/module-entitlements").header("X-Machine-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[?(@.key == 'sctracker')].download_url").value("/SCTracker.dll"));
    }

    @Test
    void succeedsWithNoPluginVersionHeaderAndWithAStaleOne() throws Exception {
        // The launcher is not SCTracker: a missing or old X-Plugin-Version must not 426 here.
        seedModule("pp-exe", true);
        String key = keyFor("noversion");

        mockMvc.perform(get("/module-entitlements").header("X-Machine-Key", key))
                .andExpect(status().isOk());
        mockMvc.perform(get("/module-entitlements")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", "1"))
                .andExpect(status().isOk());
    }
}
