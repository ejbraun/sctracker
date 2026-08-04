package com.howl.uwtracker.characters;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.characters.dto.CharacterSummaryResponse;
import com.howl.uwtracker.characters.dto.CreateCharacterRequest;
import com.howl.uwtracker.domain.GameMap;
import com.howl.uwtracker.domain.Profession;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunParticipant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** specs/backend/04-characters.md — CRUD plus the retroactive character_id backfill, against real MySQL. */
class CharacterIntegrationTest extends AbstractIntegrationTest {

    @Test
    void addCreatesACharacterOwnedByTheCaller() throws Exception {
        MockHttpSession session = signup("charowner", "password123");

        mockMvc.perform(post("/api/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCharacterRequest("My Warrior"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.character_name").value("My Warrior"));

        assertThat(playerCharacterRepository.existsByCharacterName("My Warrior")).isTrue();
    }

    @Test
    void addRejectsDuplicateCharacterName() throws Exception {
        MockHttpSession session = signup("dupechar", "password123");
        mockMvc.perform(post("/api/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCharacterRequest("Taken Name"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCharacterRequest("Taken Name"))))
                .andExpect(status().isConflict());
    }

    @Test
    void listOnlyReturnsCallersOwnCharacters() throws Exception {
        MockHttpSession ownerSession = signup("listowner", "password123");
        MockHttpSession otherSession = signup("listother", "password123");

        mockMvc.perform(post("/api/characters")
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCharacterRequest("Owner Char"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/characters").session(otherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/characters").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].character_name").value("Owner Char"));
    }

    @Test
    void removeRejectsDeletingSomeoneElsesCharacter() throws Exception {
        MockHttpSession ownerSession = signup("removeowner", "password123");
        MockHttpSession attackerSession = signup("removeattacker", "password123");
        String body = mockMvc.perform(post("/api/characters")
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCharacterRequest("Protected Char"))))
                .andReturn().getResponse().getContentAsString();
        Long characterId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(delete("/api/characters/" + characterId).session(attackerSession))
                .andExpect(status().isForbidden());
        assertThat(playerCharacterRepository.findById(characterId)).isPresent();
    }

    @Test
    void removeOfNonexistentCharacterReturns404() throws Exception {
        MockHttpSession session = signup("removenone", "password123");
        mockMvc.perform(delete("/api/characters/999999").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeDeletesTheOwnersCharacter() throws Exception {
        MockHttpSession session = signup("removeself", "password123");
        String body = mockMvc.perform(post("/api/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCharacterRequest("Doomed Char"))))
                .andReturn().getResponse().getContentAsString();
        Long characterId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(delete("/api/characters/" + characterId).session(session))
                .andExpect(status().isNoContent());
        assertThat(playerCharacterRepository.findById(characterId)).isEmpty();
    }

    @Test
    void listAllReturnsEveryCharacterSystemWideWithItsOwnersPersonId() throws Exception {
        MockHttpSession ownerSession = signup("allowner", "password123");
        Long ownerId = personRepository.findByUsername("allowner").orElseThrow().getId();
        mockMvc.perform(post("/api/characters")
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCharacterRequest("Owner Toon"))))
                .andExpect(status().isCreated());

        MockHttpSession viewerSession = signup("allviewer", "password123");
        Long viewerId = personRepository.findByUsername("allviewer").orElseThrow().getId();
        mockMvc.perform(post("/api/characters")
                        .session(viewerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCharacterRequest("Viewer Toon"))))
                .andExpect(status().isCreated());

        // Requested by the viewer, but must list the owner's character too (system-wide) — this is a
        // directory, not the caller's own /api/characters. person_id is included (unlike a raw
        // username, never exposed) so the frontend can cross-filter the person/character dropdowns
        // against each other.
        String body = mockMvc.perform(get("/api/characters/all").session(viewerSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<CharacterSummaryResponse> all = objectMapper.readValue(body,
                objectMapper.getTypeFactory().constructCollectionType(List.class, CharacterSummaryResponse.class));

        assertThat(all).hasSize(2);
        assertThat(all).filteredOn(c -> c.characterName().equals("Owner Toon"))
                .extracting(CharacterSummaryResponse::personId).containsExactly(ownerId);
        assertThat(all).filteredOn(c -> c.characterName().equals("Viewer Toon"))
                .extracting(CharacterSummaryResponse::personId).containsExactly(viewerId);
    }

    @Test
    void addRetroactivelyBackfillsCharacterIdOnPastRunParticipants() throws Exception {
        // Seed a run_participants row ingested under a raw_name before any character existed for it,
        // mirroring how the SDK plugin uploads runs by character name alone (no account link yet).
        GameMap map = gameMapRepository.getReferenceById(UNDERWORLD_MAP_ID);
        Run run = runRepository.save(new Run(map, Instant.now(), 1000L, Instant.now(), "victory", true, 5000L));
        Profession warrior = professionRepository.findById(1).orElseThrow();
        RunParticipant participant = runParticipantRepository.save(
                new RunParticipant(run, null, "PreExisting Toon", warrior, null, "T1", 0, true, false, false, 0));
        assertThat(participant.getCharacter()).isNull();

        MockHttpSession session = signup("backfiller", "password123");
        String body = mockMvc.perform(post("/api/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCharacterRequest("PreExisting Toon"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long characterId = objectMapper.readTree(body).get("id").asLong();

        // Read the raw FK column rather than navigating RunParticipant.character — that association
        // is lazy, and this repository call (like the app's own, outside a @Transactional service
        // method) has no open Hibernate session to resolve it against.
        Long backfilledCharacterId = jdbcTemplate.queryForObject(
                "SELECT character_id FROM run_participants WHERE id = ?", Long.class, participant.getId());
        assertThat(backfilledCharacterId).isEqualTo(characterId);
    }
}
