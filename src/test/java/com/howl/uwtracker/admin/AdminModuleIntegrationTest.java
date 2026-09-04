package com.howl.uwtracker.admin;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.admin.dto.CreateModuleRequest;
import com.howl.uwtracker.admin.dto.UpdateModuleRequest;
import com.howl.uwtracker.domain.ModuleType;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.plugin.FakePluginStorageConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin module registry CRUD ({@code /api/admin/modules}) + the per-user grant sub-resource. */
class AdminModuleIntegrationTest extends AbstractIntegrationTest {

    private MockHttpSession adminSession() throws Exception {
        String username = "admin-" + System.nanoTime();
        MockHttpSession session = signup(username, "password123");
        makeAdmin(personRepository.findByUsername(username).orElseThrow().getId());
        return session;
    }

    private Person user(String prefix) {
        return personRepository.save(new Person(prefix + "-" + System.nanoTime(), "hash"));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // --- bucket discovery ------------------------------------------------------------------------

    @Test
    void discoverListsUnregisteredPluginFoldersWithDerivedPaths() throws Exception {
        MockHttpSession admin = adminSession();
        FakePluginStorageConfig.PLUGIN_FOLDERS.add("SCTracker");
        FakePluginStorageConfig.PLUGIN_FOLDERS.add("PP-Vanquish");
        FakePluginStorageConfig.EMPTY_DIRS.add("StrayDir");
        // SCTracker is already registered (its plugins/SCTracker prefix), so discover skips it.
        seedModuleWithPrefix("sctracker", "plugins/SCTracker");

        mockMvc.perform(get("/api/admin/modules/discover").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].folder_name").value("PP-Vanquish"))
                .andExpect(jsonPath("$[0].suggested_key").value("pp-vanquish"))
                .andExpect(jsonPath("$[0].suggested_type").value("plugin"))
                .andExpect(jsonPath("$[0].bucket_prefix").value("plugins/PP-Vanquish"))
                .andExpect(jsonPath("$[0].artifact_object").value("PP-Vanquish.dll"))
                .andExpect(jsonPath("$[0].manifest_object").value("plugins/PP-Vanquish/PP-Vanquish.version.json"))
                .andExpect(jsonPath("$[0].has_manifest").value(true))
                // suggested display name comes from the (synthetic) manifest's name field
                .andExpect(jsonPath("$[0].suggested_display_name").value("PP-Vanquish"));
    }

    @Test
    void discoverFindsLauncherFoldersAsModulesWithNonDllArtifacts() throws Exception {
        MockHttpSession admin = adminSession();
        FakePluginStorageConfig.LAUNCHER_FOLDERS.put("gwrl-install", "gwrl-install.zip");
        FakePluginStorageConfig.LAUNCHER_FOLDERS.put("gwrl-base", "gwrl-base.exe");
        // Already registered — discover skips it.
        seedModuleWithPrefix("gwrl-foo", "launcher/gwrl-foo");
        FakePluginStorageConfig.LAUNCHER_FOLDERS.put("gwrl-foo", "gwrl-foo.dll");

        mockMvc.perform(get("/api/admin/modules/discover").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.folder_name == 'gwrl-install')].suggested_type").value("module"))
                .andExpect(jsonPath("$[?(@.folder_name == 'gwrl-install')].artifact_object").value("gwrl-install.zip"))
                .andExpect(jsonPath("$[?(@.folder_name == 'gwrl-install')].bucket_prefix").value("launcher/gwrl-install"))
                .andExpect(jsonPath("$[?(@.folder_name == 'gwrl-install')].manifest_object")
                        .value("launcher/gwrl-install/gwrl-install.version.json"))
                .andExpect(jsonPath("$[?(@.folder_name == 'gwrl-base')].suggested_type").value("module"))
                .andExpect(jsonPath("$[?(@.folder_name == 'gwrl-base')].artifact_object").value("gwrl-base.exe"))
                .andExpect(jsonPath("$[?(@.folder_name == 'gwrl-foo')]").doesNotExist());
    }

    @Test
    void discoveredModuleCanBeImportedAndThenNoLongerShowsUp() throws Exception {
        MockHttpSession admin = adminSession();
        FakePluginStorageConfig.PLUGIN_FOLDERS.add("PP-Vanquish");

        String discovered = mockMvc.perform(get("/api/admin/modules/discover").session(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var node = objectMapper.readTree(discovered).get(0);

        mockMvc.perform(post("/api/admin/modules").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateModuleRequest(
                                node.get("suggested_key").asText(), "Vanquish data", null, false,
                                node.get("bucket_prefix").asText(), node.get("artifact_object").asText(),
                                node.get("manifest_object").asText(), null, 0))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/modules/discover").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/admin/modules").session(admin))
                .andExpect(jsonPath("$[?(@.module_key == 'pp-vanquish')].bucket_prefix").value("plugins/PP-Vanquish"));
    }

    @Test
    void discoverIsEmptyWhenTheBucketHasNothing() throws Exception {
        MockHttpSession admin = adminSession();
        mockMvc.perform(get("/api/admin/modules/discover").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void discoverIsAdminOnly() throws Exception {
        MockHttpSession plain = signup("plain-discover-" + System.nanoTime(), "password123");
        mockMvc.perform(get("/api/admin/modules/discover").session(plain))
                .andExpect(status().isForbidden());
    }

    private void seedModuleWithPrefix(String key, String bucketPrefix) {
        jdbcTemplate.update(
                "INSERT INTO modules (module_key, display_name, is_public, enabled, bucket_prefix, artifact_object) "
                        + "VALUES (?, ?, 1, 1, ?, ?)",
                key, key + " module", bucketPrefix, "x.dll");
    }

    // --- registry CRUD -----------------------------------------------------------------------------

    @Test
    void createsListsUpdatesAndDeletesAModule() throws Exception {
        MockHttpSession admin = adminSession();

        mockMvc.perform(post("/api/admin/modules").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateModuleRequest("pp-vanquish", "Vanquish aggregation", null, false,
                                "plugins/pp-vanquish", "pp-vanquish.dll", null, null, 5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.module_key").value("pp-vanquish"))
                .andExpect(jsonPath("$.is_public").value(false))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.content_type").value("application/octet-stream"));

        mockMvc.perform(get("/api/admin/modules").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.module_key == 'pp-vanquish')].display_name").value("Vanquish aggregation"));

        mockMvc.perform(patch("/api/admin/modules/pp-vanquish").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateModuleRequest("Vanquish data", null, null, false, null, null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.display_name").value("Vanquish data"))
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(delete("/api/admin/modules/pp-vanquish").session(admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/modules").session(admin))
                .andExpect(jsonPath("$[?(@.module_key == 'pp-vanquish')]").isEmpty());
    }

    @Test
    void rejectsBadKeyDuplicateAndBlankFields() throws Exception {
        MockHttpSession admin = adminSession();

        mockMvc.perform(post("/api/admin/modules").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateModuleRequest("Bad Key!", "x", null, false, "p", "a.dll", null, null, null))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/modules").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateModuleRequest("pp-ok", "  ", null, false, "p", "a.dll", null, null, null))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/modules").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateModuleRequest("pp-dup", "First", null, false, "p", "a.dll", null, null, null))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/modules").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateModuleRequest("pp-dup", "Second", null, false, "p", "a.dll", null, null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void createWithTypeModuleAndPatchItBackToPlugin() throws Exception {
        MockHttpSession admin = adminSession();
        mockMvc.perform(post("/api/admin/modules").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateModuleRequest("gwrl-base", "GWRL base", ModuleType.MODULE, true,
                                "launcher/gwrl-base", "gwrl-base.exe", null, null, 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("module"));

        mockMvc.perform(patch("/api/admin/modules/gwrl-base").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateModuleRequest(null, ModuleType.PLUGIN, null, null, null, null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("plugin"));
    }

    @Test
    void typeDefaultsToPluginAndRejectsAnUnknownValue() throws Exception {
        MockHttpSession admin = adminSession();
        mockMvc.perform(post("/api/admin/modules").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateModuleRequest("pp-default", "d", null, false, "p", "a.dll", null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("plugin"));

        mockMvc.perform(post("/api/admin/modules").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"module_key\":\"pp-bad\",\"display_name\":\"d\",\"type\":\"nonsense\","
                                + "\"bucket_prefix\":\"p\",\"artifact_object\":\"a.dll\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesToDeleteASeededModuleButAllowsDisable() throws Exception {
        MockHttpSession admin = adminSession();
        seedModule("sctracker", true);

        mockMvc.perform(delete("/api/admin/modules/sctracker").session(admin))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/admin/modules/sctracker").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateModuleRequest(null, null, null, false, null, null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void updateOfAnUnknownModuleIs404() throws Exception {
        MockHttpSession admin = adminSession();
        mockMvc.perform(patch("/api/admin/modules/nope").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateModuleRequest("x", null, null, null, null, null, null, null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminIsForbiddenOnEveryRegistryVerb() throws Exception {
        MockHttpSession plain = signup("plain-" + System.nanoTime(), "password123");

        mockMvc.perform(get("/api/admin/modules").session(plain)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/modules").session(plain).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateModuleRequest("x", "x", null, false, "p", "a.dll", null, null, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/modules/x").session(plain)).andExpect(status().isForbidden());
    }

    // --- per-user grants -------------------------------------------------------------------------

    @Test
    void grantsAndRevokesForAUser() throws Exception {
        MockHttpSession admin = adminSession();
        seedModule("pp-exe", true);
        seedModule("pp-vanquish", false);

        String targetName = "grantee-" + System.nanoTime();
        MockHttpSession targetSession = signup(targetName, "password123");
        long targetId = personRepository.findByUsername(targetName).orElseThrow().getId();
        String key = generateMachineKey(targetSession, "pp");

        mockMvc.perform(get("/api/admin/users/" + targetId + "/modules").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.module_key == 'pp-vanquish')].granted").value(false));

        mockMvc.perform(put("/api/admin/users/" + targetId + "/modules/pp-vanquish").session(admin))
                .andExpect(status().isNoContent());
        // idempotent
        mockMvc.perform(put("/api/admin/users/" + targetId + "/modules/pp-vanquish").session(admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/users/" + targetId + "/modules").session(admin))
                .andExpect(jsonPath("$[?(@.module_key == 'pp-vanquish')].granted").value(true));

        // the grant is visible to the launcher immediately
        mockMvc.perform(get("/module-entitlements").header("X-Machine-Key", key))
                .andExpect(jsonPath("$.modules[?(@.key == 'pp-vanquish')].is_public").value(false));

        mockMvc.perform(delete("/api/admin/users/" + targetId + "/modules/pp-vanquish").session(admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/module-entitlements").header("X-Machine-Key", key))
                .andExpect(jsonPath("$.modules[?(@.key == 'pp-vanquish')]").isEmpty());
    }

    @Test
    void grantForUnknownUserOrModuleIs404() throws Exception {
        MockHttpSession admin = adminSession();
        seedModule("pp-vanquish", false);
        Person target = user("t");

        mockMvc.perform(put("/api/admin/users/99999999/modules/pp-vanquish").session(admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/users/" + target.getId() + "/modules/nope").session(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminCannotTouchGrants() throws Exception {
        MockHttpSession plain = signup("plain2-" + System.nanoTime(), "password123");
        Person target = user("t");

        mockMvc.perform(get("/api/admin/users/" + target.getId() + "/modules").session(plain))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/users/" + target.getId() + "/modules/x").session(plain))
                .andExpect(status().isForbidden());
    }
}
