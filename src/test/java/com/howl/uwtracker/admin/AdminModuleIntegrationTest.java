package com.howl.uwtracker.admin;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.admin.dto.CreateModuleRequest;
import com.howl.uwtracker.admin.dto.UpdateModuleRequest;
import com.howl.uwtracker.domain.Person;
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

    // --- registry CRUD -----------------------------------------------------------------------------

    @Test
    void createsListsUpdatesAndDeletesAModule() throws Exception {
        MockHttpSession admin = adminSession();

        mockMvc.perform(post("/api/admin/modules").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateModuleRequest("pp-vanquish", "Vanquish aggregation", false,
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
                        .content(json(new UpdateModuleRequest("Vanquish data", null, false, null, null, null, null, null))))
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
                        .content(json(new CreateModuleRequest("Bad Key!", "x", false, "p", "a.dll", null, null, null))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/modules").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateModuleRequest("pp-ok", "  ", false, "p", "a.dll", null, null, null))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/modules").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateModuleRequest("pp-dup", "First", false, "p", "a.dll", null, null, null))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/modules").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateModuleRequest("pp-dup", "Second", false, "p", "a.dll", null, null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void refusesToDeleteASeededModuleButAllowsDisable() throws Exception {
        MockHttpSession admin = adminSession();
        seedModule("sctracker", true);

        mockMvc.perform(delete("/api/admin/modules/sctracker").session(admin))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/admin/modules/sctracker").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateModuleRequest(null, null, false, null, null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void updateOfAnUnknownModuleIs404() throws Exception {
        MockHttpSession admin = adminSession();
        mockMvc.perform(patch("/api/admin/modules/nope").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateModuleRequest("x", null, null, null, null, null, null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminIsForbiddenOnEveryRegistryVerb() throws Exception {
        MockHttpSession plain = signup("plain-" + System.nanoTime(), "password123");

        mockMvc.perform(get("/api/admin/modules").session(plain)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/modules").session(plain).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateModuleRequest("x", "x", false, "p", "a.dll", null, null, null))))
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
