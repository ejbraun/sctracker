package com.howl.uwtracker.leaderboards;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.domain.GameMap;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.PlayerCharacter;
import com.howl.uwtracker.domain.Profession;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunObjective;
import com.howl.uwtracker.domain.RunParticipant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The party-size dimension on the leaderboards for a multi-config map (The Fissure of Woe: a
 * role-gated duo and a role-less 8-man). See specs/features/fow-and-party-size.md §9.
 */
class FowLeaderboardIntegrationTest extends AbstractIntegrationTest {

    private static final int RANGER = 2;

    @BeforeEach
    void seedMap() {
        seedFissureOfWoe();
    }

    private GameMap fow() {
        return gameMapRepository.getReferenceById(FISSURE_OF_WOE_MAP_ID);
    }

    /** A completed FoW run at {@code partySize}, with {@code person}'s character on it and a "ToC" objective clear. */
    private Run seedFowRun(GameMap map, Person person, int partySize, long durationMs, long tocDurationMs, String role) {
        Profession ranger = professionRepository.findById(RANGER).orElseThrow();
        Run run = runRepository.save(new Run(map, Instant.now(), 1000L, Instant.now(), "victory", true, durationMs, partySize));
        PlayerCharacter character = playerCharacterRepository.save(
                new PlayerCharacter(person, person.getUsername() + "-" + partySize + "-char"));
        runParticipantRepository.save(new RunParticipant(run, character, character.getCharacterName(),
                ranger, null, role, 0, true, false, false, 0, null));
        runObjectiveRepository.save(new RunObjective(run, 0, "ToC", 2, 0L, tocDurationMs, tocDurationMs, 0));
        return run;
    }

    @Test
    void overallIsFilteredByPartySize() throws Exception {
        MockHttpSession session = signup("fowlb", "password123");
        Person person = personRepository.findByUsername("fowlb").orElseThrow();
        GameMap map = fow();

        seedFowRun(map, person, 2, 10_000L, 3_000L, "Ranger");   // duo
        seedFowRun(map, person, 8, 25_000L, 6_000L, null);       // 8-man, no roles

        mockMvc.perform(get("/api/leaderboards/maps/" + FISSURE_OF_WOE_MAP_ID + "/overall").session(session).param("partySize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].duration_ms").value(10_000));

        mockMvc.perform(get("/api/leaderboards/maps/" + FISSURE_OF_WOE_MAP_ID + "/overall").session(session).param("partySize", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].duration_ms").value(25_000));
    }

    @Test
    void personalSectionBestIsUnGatedForTheRolelessEightManConfig() throws Exception {
        MockHttpSession session = signup("fowme", "password123");
        Person person = personRepository.findByUsername("fowme").orElseThrow();

        // 8-man FoW run, participant role is NULL (no role model) — the role-gated query would find
        // nothing here; the un-gated branch must still return this as the person's ToC PB.
        seedFowRun(fow(), person, 8, 25_000L, 6_000L, null);

        mockMvc.perform(get("/api/leaderboards/me/maps/" + FISSURE_OF_WOE_MAP_ID + "/sections/ToC")
                        .session(session).param("partySize", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duration_ms").value(6_000));
    }

    @Test
    void personalSectionRequiresPartySizeForAMultiConfigMap() throws Exception {
        MockHttpSession session = signup("fowme2", "password123");
        seedFowRun(fow(), personRepository.findByUsername("fowme2").orElseThrow(), 8, 25_000L, 6_000L, null);

        // FoW has two configs — an un-sized personal-section query can't pick a role model.
        mockMvc.perform(get("/api/leaderboards/me/maps/" + FISSURE_OF_WOE_MAP_ID + "/sections/ToC").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleMvpAwardsIsEmptyForTheRolelessConfig() throws Exception {
        MockHttpSession session = signup("fowrole", "password123");
        seedFowRun(fow(), personRepository.findByUsername("fowrole").orElseThrow(), 8, 25_000L, 6_000L, null);

        mockMvc.perform(get("/api/leaderboards/maps/" + FISSURE_OF_WOE_MAP_ID + "/role-mvp-awards")
                        .session(session).param("partySize", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
