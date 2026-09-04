package com.howl.uwtracker.modules;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.plugin.FakePluginStorageConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/account/modules} + {@code /{key}/download} — the session-authenticated view the
 * account page uses (public + granted for the logged-in user), and the browser-friendly download
 * route for a gated module.
 */
class AccountModuleIntegrationTest extends AbstractIntegrationTest {

    private static byte[] expectedBytes(String moduleKey) {
        return FakePluginStorageConfig.fakeArtifactBytes("plugins/" + moduleKey + "/" + moduleKey + ".dll");
    }

    private long personId(String username) {
        return personRepository.findByUsername(username).orElseThrow().getId();
    }

    @Test
    void listWithoutASessionIs401() throws Exception {
        mockMvc.perform(get("/api/account/modules"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsPublicModulesAndRewritesTheDownloadUrl() throws Exception {
        seedModule("dbbox", true, "plugin");
        seedModule("gwrl-install", false, "module");
        MockHttpSession session = signup("plain-account", "password123");

        mockMvc.perform(get("/api/account/modules").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.length()").value(1))
                .andExpect(jsonPath("$.modules[0].key").value("dbbox"))
                .andExpect(jsonPath("$.modules[0].download_url").value("/api/account/modules/dbbox/download"));
    }

    @Test
    void grantedGatedModuleAppearsWithItsAccountDownloadUrl() throws Exception {
        long gatedId = seedModule("gwrl-install", false, "module");
        MockHttpSession session = signup("granted-account", "password123");
        grantModule(personId("granted-account"), gatedId);

        mockMvc.perform(get("/api/account/modules").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[?(@.key == 'gwrl-install')].is_public").value(false))
                .andExpect(jsonPath("$.modules[?(@.key == 'gwrl-install')].download_url")
                        .value("/api/account/modules/gwrl-install/download"));
    }

    @Test
    void sctrackerKeepsItsDedicatedRoute() throws Exception {
        seedModule("sctracker", true);
        MockHttpSession session = signup("sc-account", "password123");

        mockMvc.perform(get("/api/account/modules").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[?(@.key == 'sctracker')].download_url").value("/SCTracker.dll"));
    }

    @Test
    void typeFilterNarrowsTheList() throws Exception {
        seedModule("dbbox", true, "plugin");
        seedModule("gwrl-install", true, "module");
        MockHttpSession session = signup("typed-account", "password123");

        mockMvc.perform(get("/api/account/modules?type=module").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.length()").value(1))
                .andExpect(jsonPath("$.modules[0].key").value("gwrl-install"));
    }

    @Test
    void downloadOfAGrantedGatedModuleStreamsTheBytes() throws Exception {
        long gatedId = seedModule("gwrl-install", false, "module");
        MockHttpSession session = signup("dl-granted", "password123");
        grantModule(personId("dl-granted"), gatedId);

        MvcResult result = mockMvc.perform(get("/api/account/modules/gwrl-install/download").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"gwrl-install.dll\""))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(expectedBytes("gwrl-install"));
    }

    @Test
    void downloadWithoutAGrantIs403() throws Exception {
        seedModule("gwrl-install", false, "module");
        MockHttpSession session = signup("dl-nogrant", "password123");

        mockMvc.perform(get("/api/account/modules/gwrl-install/download").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadWithoutASessionIs401() throws Exception {
        seedModule("gwrl-install", false, "module");

        mockMvc.perform(get("/api/account/modules/gwrl-install/download"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void downloadOfAnUnknownModuleIs404() throws Exception {
        MockHttpSession session = signup("dl-unknown", "password123");

        mockMvc.perform(get("/api/account/modules/nope/download").session(session))
                .andExpect(status().isNotFound());
    }
}
