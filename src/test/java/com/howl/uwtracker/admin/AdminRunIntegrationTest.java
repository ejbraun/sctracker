package com.howl.uwtracker.admin;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.domain.GameMap;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.PlayerCharacter;
import com.howl.uwtracker.domain.Profession;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunObjective;
import com.howl.uwtracker.domain.RunParticipant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin "wipe unregistered runs" cleanup against real MySQL — the cascade behavior it depends on
 * (deleting a run must take run_objectives/run_participants/run_participant_item_drops with it) is
 * a MySQL FK feature, not application code, so this needs a real database to actually prove out, not
 * just a mocked repository. Seeding style mirrors LoserboardIntegrationTest.
 */
class AdminRunIntegrationTest extends AbstractIntegrationTest {

    private GameMap map() {
        return gameMapRepository.getReferenceById(UNDERWORLD_MAP_ID);
    }

    private Run seedRun(GameMap map, RunParticipant... participants) {
        return seedRun(map, 8, participants);
    }

    private Run seedRun(GameMap map, int partySize, RunParticipant... participants) {
        Instant now = Instant.now();
        Run run = runRepository.save(new Run(map, now, 1000L, now, "victory", true, 10_000L, partySize));
        runObjectiveRepository.save(new RunObjective(run, 0, "Chamber", 2, 0L, 1000L, 1000L, 0));
        for (RunParticipant blueprint : participants) {
            runParticipantRepository.save(new RunParticipant(run, blueprint.getCharacter(), blueprint.getRawName(),
                    blueprint.getPrimaryProfession(), blueprint.getSecondaryProfession(), blueprint.getRole(),
                    blueprint.getPartyIndex(), true, false, false, 0, null));
        }
        return run;
    }

    private RunParticipant participant(PlayerCharacter character, String rawName, Profession profession, int index) {
        return new RunParticipant(null, character, rawName, profession, null, "T1", index, true, false, false, 0, null);
    }

    private PlayerCharacter character(Person person, String name) {
        return playerCharacterRepository.save(new PlayerCharacter(person, name));
    }

    private Person personEntity(String username) {
        return personRepository.save(new Person(username, "irrelevant-hash"));
    }

    private MockHttpSession adminSession() throws Exception {
        String username = "admin-" + System.nanoTime();
        MockHttpSession session = signup(username, "password123");
        makeAdmin(personRepository.findByUsername(username).orElseThrow().getId());
        return session;
    }

    @Test
    void unregisteredCountIsZeroWhenEveryRunMeetsTheThreshold() throws Exception {
        MockHttpSession session = adminSession();
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();
        Person owner = personEntity("owner-" + System.nanoTime());

        seedRun(map,
                participant(character(owner, "A" + System.nanoTime()), "A", warrior, 0),
                participant(character(owner, "B" + System.nanoTime()), "B", warrior, 1),
                participant(character(owner, "C" + System.nanoTime()), "C", warrior, 2),
                participant(character(owner, "D" + System.nanoTime()), "D", warrior, 3));

        mockMvc.perform(get("/api/admin/runs/unregistered-count").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void unregisteredCountCountsRunsBelowTheThreshold() throws Exception {
        MockHttpSession session = adminSession();
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();
        Person owner = personEntity("owner-" + System.nanoTime());

        // 3 registered — below the minimum of 4.
        seedRun(map,
                participant(character(owner, "E" + System.nanoTime()), "E", warrior, 0),
                participant(character(owner, "F" + System.nanoTime()), "F", warrior, 1),
                participant(character(owner, "G" + System.nanoTime()), "G", warrior, 2));
        // 4 registered — exactly at the minimum, not flagged.
        seedRun(map,
                participant(character(owner, "H" + System.nanoTime()), "H", warrior, 0),
                participant(character(owner, "I" + System.nanoTime()), "I", warrior, 1),
                participant(character(owner, "J" + System.nanoTime()), "J", warrior, 2),
                participant(character(owner, "K" + System.nanoTime()), "K", warrior, 3));
        // Zero participants at all — still correctly counted as 0 registered, not silently skipped.
        seedRun(map);

        mockMvc.perform(get("/api/admin/runs/unregistered-count").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void wipeDeletesOnlyRunsBelowThresholdAndCascadesParticipantsAndObjectives() throws Exception {
        MockHttpSession session = adminSession();
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();
        Person owner = personEntity("owner-" + System.nanoTime());

        Run unregistered = seedRun(map,
                participant(character(owner, "L" + System.nanoTime()), "L", warrior, 0),
                participant(character(owner, "M" + System.nanoTime()), "M", warrior, 1));
        Run registered = seedRun(map,
                participant(character(owner, "N" + System.nanoTime()), "N", warrior, 0),
                participant(character(owner, "O" + System.nanoTime()), "O", warrior, 1),
                participant(character(owner, "P" + System.nanoTime()), "P", warrior, 2),
                participant(character(owner, "Q" + System.nanoTime()), "Q", warrior, 3));

        mockMvc.perform(post("/api/admin/runs/wipe-unregistered").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted_count").value(1));

        assertThat(runRepository.findById(unregistered.getId())).isEmpty();
        assertThat(runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(unregistered.getId())).isEmpty();
        assertThat(runObjectiveRepository.findByRun_IdOrderBySequenceAsc(unregistered.getId())).isEmpty();

        assertThat(runRepository.findById(registered.getId())).isPresent();
        assertThat(runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(registered.getId())).hasSize(4);
        assertThat(runObjectiveRepository.findByRun_IdOrderBySequenceAsc(registered.getId())).hasSize(1);
    }

    @Test
    void thresholdIsHalfEachRunsOwnPartySize() throws Exception {
        // A Fissure of Woe duo needs only 1 of 2 registered (party_size / 2). The old fixed "4"
        // would have swept both of these up.
        MockHttpSession session = adminSession();
        seedFissureOfWoe();
        GameMap fow = gameMapRepository.getReferenceById(FISSURE_OF_WOE_MAP_ID);
        Profession ranger = professionRepository.findById(2).orElseThrow();
        Person owner = personEntity("fowowner-" + System.nanoTime());

        Run keptDuo = seedRun(fow, 2,
                participant(character(owner, "duo-reg-" + System.nanoTime()), "duo-reg", ranger, 0),
                participant(null, "duo-unreg", ranger, 1));
        Run wipedDuo = seedRun(fow, 2,
                participant(null, "duo-a", ranger, 0),
                participant(null, "duo-b", ranger, 1));

        mockMvc.perform(post("/api/admin/runs/wipe-unregistered").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted_count").value(1));

        assertThat(runRepository.findById(keptDuo.getId())).isPresent();
        assertThat(runRepository.findById(wipedDuo.getId())).isEmpty();
    }

    @Test
    void wipeRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/api/admin/runs/wipe-unregistered"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wipeRejectsNonAdminSession() throws Exception {
        MockHttpSession session = signup("not-admin-" + System.nanoTime(), "password123");
        mockMvc.perform(post("/api/admin/runs/wipe-unregistered").session(session))
                .andExpect(status().isForbidden());
    }
}
