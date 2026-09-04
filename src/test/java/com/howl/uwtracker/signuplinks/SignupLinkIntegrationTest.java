package com.howl.uwtracker.signuplinks;

import com.fasterxml.jackson.databind.JsonNode;
import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.auth.dto.SignupRequest;
import com.howl.uwtracker.web.MachineKeyHasher;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * specs/backend/03-auth.md — admin-minted multi-use signup links, and their redemption through
 * {@code POST /api/signup}. {@code people} is truncated before each test, so plain usernames are safe.
 */
class SignupLinkIntegrationTest extends AbstractIntegrationTest {

    private MockHttpSession adminSession() throws Exception {
        MockHttpSession session = signup("linkadmin", "password123");
        makeAdmin(personRepository.findByUsername("linkadmin").orElseThrow().getId());
        return session;
    }

    /** POST /api/admin/signup-links with the given JSON body; returns the raw token. */
    private String createLink(MockHttpSession session, String bodyJson) throws Exception {
        String body = mockMvc.perform(post("/api/admin/signup-links")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private void signupWith(String username, String token, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(username, "password123", token))))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void generatesALinkWithTheDefaultCapAndReturnsTheTokenOnce() throws Exception {
        MockHttpSession session = adminSession();

        String body = mockMvc.perform(post("/api/admin/signup-links")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.max_uses").value(10))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(body).get("token").asText();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM signup_links WHERE token_hash = ?", Integer.class, MachineKeyHasher.hash(token)))
                .isEqualTo(1);
    }

    @Test
    void honoursAnExplicitMaxUsesAndRejectsAnOutOfRangeOne() throws Exception {
        MockHttpSession session = adminSession();

        mockMvc.perform(post("/api/admin/signup-links").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"max_uses\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.max_uses").value(3));

        mockMvc.perform(post("/api/admin/signup-links").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"max_uses\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/signup-links").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"max_uses\":101}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listNeverLeaksTheTokenOrItsHash() throws Exception {
        MockHttpSession session = adminSession();
        createLink(session, "{\"label\":\"Discord\"}");

        String body = mockMvc.perform(get("/api/admin/signup-links").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label").value("Discord"))
                .andExpect(jsonPath("$[0].use_count").value(0))
                .andExpect(jsonPath("$[0].max_uses").value(10))
                .andReturn().getResponse().getContentAsString();

        JsonNode row = objectMapper.readTree(body).get(0);
        assertThat(row.has("token")).isFalse();
        assertThat(row.has("token_hash")).isFalse();
    }

    @Test
    void requiresAdmin() throws Exception {
        MockHttpSession plain = signup("notadmin", "password123");

        mockMvc.perform(get("/api/admin/signup-links").session(plain)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/signup-links").session(plain)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/signup-links/1").session(plain)).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/signup-links")).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenSignsPeopleUpAndBumpsTheUseCount() throws Exception {
        MockHttpSession session = adminSession();
        String token = createLink(session, "{}");

        signupWith("vialink1", token, 201);
        signupWith("vialink2", token, 201);
        assertThat(personRepository.findByUsername("vialink1")).isPresent();

        mockMvc.perform(get("/api/admin/signup-links").session(session))
                .andExpect(jsonPath("$[0].use_count").value(2));
    }

    @Test
    void stopsRedeemingOnceTheCapIsHit() throws Exception {
        MockHttpSession session = adminSession();
        String token = createLink(session, "{\"max_uses\":2}");

        signupWith("cap1", token, 201);
        signupWith("cap2", token, 201);
        signupWith("cap3", token, 400);
        assertThat(personRepository.findByUsername("cap3")).isEmpty();
    }

    @Test
    void revokedLinkStopsRedeemingButStaysInTheList() throws Exception {
        MockHttpSession session = adminSession();

        String createBody = mockMvc.perform(post("/api/admin/signup-links").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createBody);
        long id = created.get("id").asLong();
        String token = created.get("token").asText();

        mockMvc.perform(delete("/api/admin/signup-links/" + id).session(session)).andExpect(status().isNoContent());

        signupWith("afterrevoke", token, 400);
        mockMvc.perform(get("/api/admin/signup-links").session(session))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].revoked_at").isNotEmpty());
    }

    @Test
    void revokeOfAnUnknownLinkIs404() throws Exception {
        mockMvc.perform(delete("/api/admin/signup-links/999999").session(adminSession()))
                .andExpect(status().isNotFound());
    }

    @Test
    void singleUseSignupKeysStillWork() throws Exception {
        signupWith("stillworks", freshSignupKey(), 201);
    }
}
