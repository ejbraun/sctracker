package com.howl.uwtracker.characters;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.domain.GameMap;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.PlayerCharacter;
import com.howl.uwtracker.domain.Profession;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunParticipant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /sync-characters} — GW Launcher Reforged's locally-detected character list, bulk
 * version of {@code /upload-run}'s own single-character auto-claim. Machine-key authed, same
 * {@code authenticateWithoutVersionCheck} bar as {@code /module-entitlements}.
 */
class CharacterSyncIntegrationTest extends AbstractIntegrationTest {

    private Person user(String username) {
        return personRepository.save(new Person(username, "irrelevant-hash"));
    }

    private String sync(String key, String body) throws Exception {
        return mockMvc.perform(post("/sync-characters")
                        .header("X-Machine-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void missingMachineKeyIs401() throws Exception {
        mockMvc.perform(post("/sync-characters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidMachineKeyIs401() throws Exception {
        mockMvc.perform(post("/sync-characters")
                        .header("X-Machine-Key", "not-a-real-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registersEveryPreviouslyUnregisteredNameInOrder() throws Exception {
        MockHttpSession session = signup("synccaller-" + System.nanoTime(), "password123");
        String key = generateMachineKey(session, "gwrl");

        mockMvc.perform(post("/sync-characters")
                        .header("X-Machine-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of("Alt One", "Alt Two"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added", contains("Alt One", "Alt Two")));

        assertThat(playerCharacterRepository.existsByCharacterName("Alt One")).isTrue();
        assertThat(playerCharacterRepository.existsByCharacterName("Alt Two")).isTrue();
    }

    @Test
    void skipsANameAlreadyRegisteredToTheCallerOrAnyoneElseWithoutError() throws Exception {
        String username = "syncowner-" + System.nanoTime();
        MockHttpSession session = signup(username, "password123");
        String key = generateMachineKey(session, "gwrl");
        Person caller = personRepository.findByUsername(username).orElseThrow();

        // Already registered to the caller themselves.
        playerCharacterRepository.save(new PlayerCharacter(caller, "Already Mine"));

        // Already registered to a different account entirely — must never be reassigned.
        Person other = user("someone-else-" + System.nanoTime());
        playerCharacterRepository.save(new PlayerCharacter(other, "Someone Elses"));

        String response = sync(key, objectMapper.writeValueAsString(
                List.of("Already Mine", "Someone Elses", "Brand New")));

        assertThat(objectMapper.readTree(response).get("added")).hasSize(1);
        assertThat(objectMapper.readTree(response).get("added").get(0).asText()).isEqualTo("Brand New");
        // "Someone Elses" still belongs to `other`, not the caller.
        assertThat(playerCharacterRepository.findByCharacterName("Someone Elses").orElseThrow()
                .getPerson().getId()).isEqualTo(other.getId());
    }

    @Test
    void toleratesBlankAndDuplicateEntries() throws Exception {
        MockHttpSession session = signup("syncdupe-" + System.nanoTime(), "password123");
        String key = generateMachineKey(session, "gwrl");

        String response = sync(key, objectMapper.writeValueAsString(
                List.of("", "  ", "Dupe Toon", "Dupe Toon", "Dupe Toon ")));

        // "Dupe Toon " trims to the same name as "Dupe Toon" and is claimed only once.
        assertThat(objectMapper.readTree(response).get("added")).hasSize(1);
        assertThat(objectMapper.readTree(response).get("added").get(0).asText()).isEqualTo("Dupe Toon");
    }

    @Test
    void emptyArrayReturnsAnEmptyAddedList() throws Exception {
        MockHttpSession session = signup("syncempty-" + System.nanoTime(), "password123");
        String key = generateMachineKey(session, "gwrl");

        mockMvc.perform(post("/sync-characters")
                        .header("X-Machine-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").isEmpty());
    }

    @Test
    void registeringANameBackfillsPastRunParticipants() throws Exception {
        GameMap map = gameMapRepository.getReferenceById(UNDERWORLD_MAP_ID);
        Run run = runRepository.save(new Run(map, Instant.now(), 1000L, Instant.now(), "victory", true, 5000L, 8));
        Profession warrior = professionRepository.findById(1).orElseThrow();
        RunParticipant participant = runParticipantRepository.save(
                new RunParticipant(run, null, "Legacy Sync Toon", warrior, null, "T1", 0, true, false, false, 0, null));

        MockHttpSession session = signup("syncbackfill-" + System.nanoTime(), "password123");
        String key = generateMachineKey(session, "gwrl");
        String response = sync(key, objectMapper.writeValueAsString(List.of("Legacy Sync Toon")));
        Long characterId = playerCharacterRepository.findByCharacterName("Legacy Sync Toon").orElseThrow().getId();
        assertThat(objectMapper.readTree(response).get("added").get(0).asText()).isEqualTo("Legacy Sync Toon");

        Long backfilled = jdbcTemplate.queryForObject(
                "SELECT character_id FROM run_participants WHERE id = ?", Long.class, participant.getId());
        assertThat(backfilled).isEqualTo(characterId);
    }
}
