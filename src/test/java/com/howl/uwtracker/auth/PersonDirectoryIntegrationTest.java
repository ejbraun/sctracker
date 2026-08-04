package com.howl.uwtracker.auth;

import com.howl.uwtracker.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/people — backs the Run History "person" filter dropdown (see run-history spec's
 * combinable filters). Only people who've set an alias are listed; nothing here exposes a
 * {@code username}, which stays private/login-only.
 */
class PersonDirectoryIntegrationTest extends AbstractIntegrationTest {

    @Test
    void listsOnlyPeopleWithAnAliasSetOrderedAlphabetically() throws Exception {
        MockHttpSession viewer = signup("directoryviewer", "password123");

        MockHttpSession withAlias = signup("hasalias", "password123");
        mockMvc.perform(patch("/api/account/alias")
                        .session(withAlias)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"Zed\"}"));
        MockHttpSession withAlias2 = signup("hasalias2", "password123");
        mockMvc.perform(patch("/api/account/alias")
                        .session(withAlias2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"Anna\"}"));
        signup("noalias", "password123"); // never sets one — must not appear

        mockMvc.perform(get("/api/people").session(viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].alias").value("Anna"))
                .andExpect(jsonPath("$[1].alias").value("Zed"))
                .andExpect(jsonPath("$[0].username").doesNotExist());
    }

    @Test
    void requiresAnActiveSession() throws Exception {
        mockMvc.perform(get("/api/people"))
                .andExpect(status().isUnauthorized());
    }
}
