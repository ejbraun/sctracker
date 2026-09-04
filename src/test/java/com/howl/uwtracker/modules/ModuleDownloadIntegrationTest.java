package com.howl.uwtracker.modules;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.plugin.FakePluginStorageConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /modules/{key}/download} — proxies bytes from the bucket, re-checking entitlement per call. */
class ModuleDownloadIntegrationTest extends AbstractIntegrationTest {

    private static byte[] expectedBytes(String moduleKey) {
        return FakePluginStorageConfig.fakeArtifactBytes("plugins/" + moduleKey + "/" + moduleKey + ".dll");
    }

    @Test
    void publicModuleDownloadsWithoutAKey() throws Exception {
        seedModule("pp-base", true);

        MvcResult result = mockMvc.perform(get("/modules/pp-base/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"pp-base.dll\""))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(expectedBytes("pp-base"));
    }

    @Test
    void gatedModuleWithoutAKeyIs401() throws Exception {
        seedModule("pp-vanquish", false);

        mockMvc.perform(get("/modules/pp-vanquish/download"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gatedModuleWithAKeyButNoGrantIs403() throws Exception {
        seedModule("pp-vanquish", false);
        MockHttpSession session = signup("nogrant", "password123");
        String key = generateMachineKey(session, "pp");

        mockMvc.perform(get("/modules/pp-vanquish/download").header("X-Machine-Key", key))
                .andExpect(status().isForbidden());
    }

    @Test
    void gatedModuleWithAGrantDownloads() throws Exception {
        long moduleId = seedModule("pp-vanquish", false);
        MockHttpSession session = signup("granted", "password123");
        String key = generateMachineKey(session, "pp");
        long personId = personRepository.findByUsername("granted").orElseThrow().getId();
        grantModule(personId, moduleId);

        MvcResult result = mockMvc.perform(get("/modules/pp-vanquish/download").header("X-Machine-Key", key))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(expectedBytes("pp-vanquish"));
    }

    @Test
    void revokedKeyIs401() throws Exception {
        seedModule("pp-vanquish", false);
        MockHttpSession session = signup("revoked", "password123");
        String key = generateMachineKey(session, "pp");
        jdbcTemplate.update("UPDATE machine_keys SET revoked_at = NOW(6) WHERE key_hash = ?",
                com.howl.uwtracker.web.MachineKeyHasher.hash(key));

        mockMvc.perform(get("/modules/pp-vanquish/download").header("X-Machine-Key", key))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownModuleIs404() throws Exception {
        mockMvc.perform(get("/modules/does-not-exist/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    void disabledModuleIs404() throws Exception {
        long id = seedModule("pp-retired", true);
        jdbcTemplate.update("UPDATE modules SET enabled = 0 WHERE id = ?", id);

        mockMvc.perform(get("/modules/pp-retired/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    void setsEtagOnceTheManifestVersionIsKnown() throws Exception {
        seedModule("pp-vanquish", true);
        // /artifacts populates modules.current_sha256 via ModuleManifestCache
        mockMvc.perform(get("/artifacts")).andExpect(status().isOk());

        String sha = jdbcTemplate.queryForObject(
                "SELECT current_sha256 FROM modules WHERE module_key = 'pp-vanquish'", String.class);
        assertThat(sha).isNotBlank();

        mockMvc.perform(get("/modules/pp-vanquish/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + sha + "\""));
    }

    @Test
    void bodyIsNotEmpty() throws Exception {
        seedModule("pp-base", true);
        MvcResult result = mockMvc.perform(get("/modules/pp-base/download")).andReturn();
        assertThat(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                .startsWith("fake-artifact:");
    }

    @Test
    void patchNotesStreamsTextForAPublicModule() throws Exception {
        seedModuleWithPatchNotes("pp-base", true, "plugin");

        MvcResult result = mockMvc.perform(get("/modules/pp-base/patch-notes"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"pp-base.patch.txt\""))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .isEqualTo(new String(FakePluginStorageConfig.fakePatchNotesBytes("plugins/pp-base/pp-base.patch.txt"),
                        StandardCharsets.UTF_8));
    }

    @Test
    void patchNotesIs404WhenNoneConfigured() throws Exception {
        seedModule("pp-base", true);

        mockMvc.perform(get("/modules/pp-base/patch-notes"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchNotesOfAGatedModuleWithoutAKeyIs401() throws Exception {
        seedModuleWithPatchNotes("pp-vanquish", false, "plugin");

        mockMvc.perform(get("/modules/pp-vanquish/patch-notes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchNotesOfAGatedModuleWithAGrantStreams() throws Exception {
        long moduleId = seedModuleWithPatchNotes("pp-vanquish", false, "plugin");
        MockHttpSession session = signup("patch-granted", "password123");
        String key = generateMachineKey(session, "pp");
        long personId = personRepository.findByUsername("patch-granted").orElseThrow().getId();
        grantModule(personId, moduleId);

        mockMvc.perform(get("/modules/pp-vanquish/patch-notes").header("X-Machine-Key", key))
                .andExpect(status().isOk());
    }

    @Test
    void patchNotesOfAnUnknownModuleIs404() throws Exception {
        mockMvc.perform(get("/modules/does-not-exist/patch-notes"))
                .andExpect(status().isNotFound());
    }
}
