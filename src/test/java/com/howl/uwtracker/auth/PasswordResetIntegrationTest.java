package com.howl.uwtracker.auth;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.web.MachineKeyHasher;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin-issued password reset: {@code POST /api/admin/users/{id}/password-reset} mints a single-use,
 * expiring link; {@code POST /api/password-reset} redeems it. {@code people} is truncated before
 * each test, so plain usernames are safe.
 */
class PasswordResetIntegrationTest extends AbstractIntegrationTest {

    private MockHttpSession adminSession() throws Exception {
        MockHttpSession session = signup("resetadmin", "password123");
        makeAdmin(personRepository.findByUsername("resetadmin").orElseThrow().getId());
        return session;
    }

    private Long idOf(String username) {
        return personRepository.findByUsername(username).orElseThrow().getId();
    }

    /** Mints a reset link for {@code personId} as the given admin; returns the raw token. */
    private String mintToken(MockHttpSession admin, Long personId) throws Exception {
        String body = mockMvc.perform(post("/api/admin/users/" + personId + "/password-reset").session(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expires_at").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private void redeem(String token, String password, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("token", token, "password", password))))
                .andExpect(status().is(expectedStatus));
    }

    private int loginStatus(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("username", username, "password", password))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void adminMintsALinkAndOnlyTheHashIsStored() throws Exception {
        MockHttpSession admin = adminSession();
        signup("victim", "password123");

        String token = mintToken(admin, idOf("victim"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE token_hash = ?", Integer.class,
                MachineKeyHasher.hash(token))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE token_hash = ?", Integer.class, token)).isZero();
    }

    @Test
    void nonAdminCannotMintALink() throws Exception {
        MockHttpSession user = signup("plainuser", "password123");
        signup("victim", "password123");

        mockMvc.perform(post("/api/admin/users/" + idOf("victim") + "/password-reset").session(user))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users/" + idOf("victim") + "/password-reset"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mintingForAnUnknownUserIs404() throws Exception {
        MockHttpSession admin = adminSession();

        mockMvc.perform(post("/api/admin/users/999999/password-reset").session(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void redeemingSetsTheNewPasswordAndDoesNotStartASession() throws Exception {
        MockHttpSession admin = adminSession();
        signup("victim", "oldpassword1");
        String token = mintToken(admin, idOf("victim"));

        redeem(token, "brandnewpass1", 204);

        assertThat(loginStatus("victim", "oldpassword1")).isEqualTo(401);
        assertThat(loginStatus("victim", "brandnewpass1")).isEqualTo(200);
    }

    @Test
    void aLinkIsSingleUse() throws Exception {
        MockHttpSession admin = adminSession();
        signup("victim", "oldpassword1");
        String token = mintToken(admin, idOf("victim"));

        redeem(token, "brandnewpass1", 204);
        redeem(token, "anotherpass22", 400);

        assertThat(loginStatus("victim", "brandnewpass1")).isEqualTo(200);
    }

    @Test
    void anExpiredLinkIsRejected() throws Exception {
        MockHttpSession admin = adminSession();
        signup("victim", "oldpassword1");
        String token = mintToken(admin, idOf("victim"));
        jdbcTemplate.update("UPDATE password_reset_tokens SET expires_at = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)));

        redeem(token, "brandnewpass1", 400);

        assertThat(loginStatus("victim", "oldpassword1")).isEqualTo(200);
    }

    @Test
    void unknownAndBlankTokensAreRejected() throws Exception {
        redeem("not-a-real-token", "brandnewpass1", 400);
        redeem("", "brandnewpass1", 400);
    }

    @Test
    void aTooShortPasswordIsRejectedAndDoesNotBurnTheLink() throws Exception {
        MockHttpSession admin = adminSession();
        signup("victim", "oldpassword1");
        String token = mintToken(admin, idOf("victim"));

        redeem(token, "short", 400);
        redeem(token, "brandnewpass1", 204);
    }

    @Test
    void mintingANewLinkSupersedesTheOldOne() throws Exception {
        MockHttpSession admin = adminSession();
        signup("victim", "oldpassword1");
        String first = mintToken(admin, idOf("victim"));
        String second = mintToken(admin, idOf("victim"));

        redeem(first, "brandnewpass1", 400);
        redeem(second, "brandnewpass1", 204);
    }

    @Test
    void aLinkOnlyAffectsItsOwnUser() throws Exception {
        MockHttpSession admin = adminSession();
        signup("victim", "oldpassword1");
        signup("bystander", "bystander123");
        String token = mintToken(admin, idOf("victim"));

        redeem(token, "brandnewpass1", 204);

        assertThat(loginStatus("bystander", "bystander123")).isEqualTo(200);
    }

    @Test
    void redeemingRevokesTheUsersExistingSessions() throws Exception {
        MockHttpSession admin = adminSession();
        signup("victim", "oldpassword1");
        MockHttpSession oldSession = login("victim", "oldpassword1");
        mockMvc.perform(get("/api/account/me").session(oldSession)).andExpect(status().isOk());

        redeem(mintToken(admin, idOf("victim")), "brandnewpass1", 204);

        mockMvc.perform(get("/api/account/me").session(oldSession)).andExpect(status().isUnauthorized());

        MockHttpSession newSession = login("victim", "brandnewpass1");
        mockMvc.perform(get("/api/account/me").session(newSession)).andExpect(status().isOk());
        // Other users' sessions are untouched.
        mockMvc.perform(get("/api/account/me").session(admin)).andExpect(status().isOk());
    }
}
