package com.howl.uwtracker.auth;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.auth.dto.GeneratedMachineKeyResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** specs/backend/03-auth.md — machine-key self-service (generate/list/revoke) against real MySQL. */
class MachineKeyIntegrationTest extends AbstractIntegrationTest {

    @Test
    void generateRevealsRawKeyOnceAndListDoesNotExposeItAgain() throws Exception {
        MockHttpSession session = signup("keyowner", "password123");

        String body = mockMvc.perform(post("/api/account/machine-keys")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"my laptop\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").exists())
                .andExpect(jsonPath("$.label").value("my laptop"))
                .andReturn().getResponse().getContentAsString();
        GeneratedMachineKeyResponse generated = objectMapper.readValue(body, GeneratedMachineKeyResponse.class);
        assertThat(generated.key()).isNotBlank();

        mockMvc.perform(get("/api/account/machine-keys").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(generated.id()))
                .andExpect(jsonPath("$[0].label").value("my laptop"))
                .andExpect(jsonPath("$[0].key").doesNotExist());
    }

    @Test
    void listOnlyReturnsTheCallersOwnKeys() throws Exception {
        MockHttpSession ownerSession = signup("owner", "password123");
        MockHttpSession otherSession = signup("other", "password123");

        mockMvc.perform(post("/api/account/machine-keys")
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"owner-key\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/account/machine-keys").session(otherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void revokeSucceedsForOwnerAndKeyStopsWorking() throws Exception {
        MockHttpSession session = signup("revokeowner", "password123");
        String body = mockMvc.perform(post("/api/account/machine-keys")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"to-revoke\"}"))
                .andReturn().getResponse().getContentAsString();
        GeneratedMachineKeyResponse generated = objectMapper.readValue(body, GeneratedMachineKeyResponse.class);

        mockMvc.perform(delete("/api/account/machine-keys/" + generated.id()).session(session))
                .andExpect(status().isNoContent());

        assertThat(machineKeyRepository.findById(generated.id()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test
    void revokeRejectsAKeyBelongingToSomeoneElse() throws Exception {
        MockHttpSession ownerSession = signup("realowner", "password123");
        MockHttpSession attackerSession = signup("attacker", "password123");
        String body = mockMvc.perform(post("/api/account/machine-keys")
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"protected\"}"))
                .andReturn().getResponse().getContentAsString();
        GeneratedMachineKeyResponse generated = objectMapper.readValue(body, GeneratedMachineKeyResponse.class);

        mockMvc.perform(delete("/api/account/machine-keys/" + generated.id()).session(attackerSession))
                .andExpect(status().isForbidden());

        assertThat(machineKeyRepository.findById(generated.id()).orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    void revokeOfNonexistentKeyReturns404() throws Exception {
        MockHttpSession session = signup("nokeyowner", "password123");
        mockMvc.perform(delete("/api/account/machine-keys/999999").session(session))
                .andExpect(status().isNotFound());
    }
}
