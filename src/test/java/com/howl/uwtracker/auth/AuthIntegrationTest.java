package com.howl.uwtracker.auth;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.auth.dto.LoginRequest;
import com.howl.uwtracker.auth.dto.SignupRequest;
import com.howl.uwtracker.web.MachineKeyHasher;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** specs/backend/03-auth.md — signup/login/logout/me session round trip against real MySQL. */
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void signupCreatesPersonAndStartsASession() throws Exception {
        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest("newplayer", "password123", freshSignupKey()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newplayer"))
                .andExpect(jsonPath("$.id").exists());

        assertPersonExists("newplayer");
    }

    @Test
    void signupRejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest("shortpw", "short", freshSignupKey()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signupRejectsDuplicateUsername() throws Exception {
        signup("dupe", "password123");
        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest("dupe", "differentpassword", freshSignupKey()))))
                .andExpect(status().isConflict());
    }

    @Test
    void signupRejectsMissingSignupKey() throws Exception {
        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest("nokeyplayer", "password123", null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signupRejectsAnUnknownSignupKey() throws Exception {
        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest("badkeyplayer", "password123", "not-a-real-key"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signupRejectsAnAlreadyUsedSignupKey() throws Exception {
        String key = freshSignupKey();
        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest("firstuser", "password123", key))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest("seconduser", "password123", key))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signupMarksTheSignupKeyUsedAndLinksItToTheNewPerson() throws Exception {
        String key = freshSignupKey();
        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest("keyredeemer", "password123", key))))
                .andExpect(status().isCreated());

        Long personId = personRepository.findByUsername("keyredeemer").orElseThrow().getId();
        String hash = MachineKeyHasher.hash(key);
        Long usedByPersonId = jdbcTemplate.queryForObject(
                "SELECT used_by_person_id FROM signup_keys WHERE key_hash = ? AND used_at IS NOT NULL", Long.class, hash);
        assertThat(usedByPersonId).isEqualTo(personId);
    }

    @Test
    void loginSucceedsWithCorrectCredentialsAndStartsASession() throws Exception {
        signup("logger", "password123");

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("logger", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("logger"));

        mockMvc.perform(get("/api/account/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("logger"));
    }

    @Test
    void loginFailsWithWrongPasswordAndDoesNotLeakWhichFieldWasWrong() throws Exception {
        signup("victim", "correctpassword");

        MockHttpSession session = new MockHttpSession();
        String wrongPasswordBody = mockMvc.perform(post("/api/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("victim", "wrongpassword"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownUserBody = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("nosuchuser", "whatever1"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(wrongPasswordBody).isEqualTo(unknownUserBody);
    }

    @Test
    void meRequiresAnActiveSession() throws Exception {
        mockMvc.perform(get("/api/account/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesTheSessionSoSubsequentRequestsAreUnauthorized() throws Exception {
        MockHttpSession session = signup("logouttest", "password123");

        mockMvc.perform(get("/api/account/me").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/logout").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/account/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRejectRequestsWithNoSession() throws Exception {
        mockMvc.perform(get("/api/characters"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/runs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/maps"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aliasCanBeSetAndThenAppearsOnMe() throws Exception {
        MockHttpSession session = signup("aliassetter", "password123");

        mockMvc.perform(patch("/api/account/alias")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"Howl\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("Howl"));

        mockMvc.perform(get("/api/account/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("Howl"));
    }

    @Test
    void aliasCanBeClearedWithABlankValue() throws Exception {
        MockHttpSession session = signup("aliasclearer", "password123");
        mockMvc.perform(patch("/api/account/alias")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"Temp\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/account/alias")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").doesNotExist());
    }

    @Test
    void aliasRejectsOneAlreadyTakenBySomeoneElse() throws Exception {
        MockHttpSession firstSession = signup("aliasowner", "password123");
        mockMvc.perform(patch("/api/account/alias")
                        .session(firstSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"Taken\"}"))
                .andExpect(status().isOk());

        MockHttpSession secondSession = signup("aliaswanter", "password123");
        mockMvc.perform(patch("/api/account/alias")
                        .session(secondSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"Taken\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void aliasCanBeReAssignedTheCallersOwnCurrentValueWithoutConflict() throws Exception {
        MockHttpSession session = signup("aliasresaver", "password123");
        mockMvc.perform(patch("/api/account/alias")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"SameAlias\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/account/alias")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"SameAlias\"}"))
                .andExpect(status().isOk());
    }

    private void assertPersonExists(String username) {
        assertThat(personRepository.findByUsername(username)).isPresent();
    }
}
