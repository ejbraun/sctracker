package com.howl.uwtracker.leaderboards;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.domain.GameMap;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.PlayerCharacter;
import com.howl.uwtracker.domain.Profession;
import com.howl.uwtracker.domain.RoleObjective;
import com.howl.uwtracker.domain.RoleObjectiveId;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunObjective;
import com.howl.uwtracker.domain.RunParticipant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * specs/backend/05-leaderboards.md against real MySQL — including the lazy-loading DTO-mapping path
 * IMPLEMENTATION_PROGRESS.md flags as previously unverified against a live database
 * ({@code LeaderboardService.overall}/{@code section} touching {@code RunParticipant.character} and
 * {@code RunObjective.run} after the repository call returns).
 */
class LeaderboardIntegrationTest extends AbstractIntegrationTest {

    private static final int MAP_ID = UNDERWORLD_MAP_ID;
    private static final String OBJECTIVE_NAME = "The Vale";

    /** Pre-seeded by 011-seed-supported-maps.xml (reset before every test — see AbstractIntegrationTest). */
    private GameMap map() {
        return gameMapRepository.getReferenceById(MAP_ID);
    }

    private Run seedRun(GameMap map, long durationMs, boolean completed, RunParticipant... participants) {
        return seedRun(map, Instant.now(), durationMs, completed, participants);
    }

    private Run seedRun(GameMap map, Instant utcStart, long durationMs, boolean completed, RunParticipant... participants) {
        Run run = runRepository.save(new Run(map, utcStart, 1000L, utcStart, "victory", completed, durationMs));
        for (RunParticipant blueprint : participants) {
            runParticipantRepository.save(new RunParticipant(run, blueprint.getCharacter(), blueprint.getRawName(),
                    blueprint.getPrimaryProfession(), blueprint.getSecondaryProfession(), blueprint.getRole(),
                    blueprint.getPartyIndex(), true, false, false, blueprint.getDeaths()));
        }
        return run;
    }

    private RunParticipant participant(PlayerCharacter character, String rawName, Profession profession, String role, int index) {
        return new RunParticipant(null, character, rawName, profession, null, role, index, true, false, false, 0);
    }

    private PlayerCharacter character(Person person, String name) {
        return playerCharacterRepository.save(new PlayerCharacter(person, name));
    }

    private Person personEntity(String username) {
        return personRepository.save(new Person(username, "irrelevant-hash"));
    }

    @Test
    void overallReturnsCompletedRunsFastestFirstRespectingLimit() throws Exception {
        MockHttpSession session = signup("lbviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        seedRun(map, 30_000L, true, participant(null, "Slow", warrior, "T1", 0));
        seedRun(map, 10_000L, true, participant(null, "Fast", warrior, "T1", 0));
        seedRun(map, 20_000L, true, participant(null, "Medium", warrior, "T1", 0));
        seedRun(map, 5_000L, false, participant(null, "Incomplete", warrior, "T1", 0)); // excluded

        mockMvc.perform(get("/api/leaderboards/maps/" + MAP_ID + "/overall").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].duration_ms").value(10_000))
                .andExpect(jsonPath("$[0].participants[0].raw_name").value("Fast"))
                .andExpect(jsonPath("$[1].duration_ms").value(20_000))
                .andExpect(jsonPath("$[2].duration_ms").value(30_000));

        mockMvc.perform(get("/api/leaderboards/maps/" + MAP_ID + "/overall").session(session).param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void sectionReturnsFastestObjectiveTimesForThatMapAndObjectiveName() throws Exception {
        MockHttpSession session = signup("sectionviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        Run slowRun = seedRun(map, 40_000L, true, participant(null, "P1", warrior, "T1", 0));
        runObjectiveRepository.save(new RunObjective(slowRun, 0, OBJECTIVE_NAME, 2, 0L, 8000L, 8000L, 0));
        Run fastRun = seedRun(map, 35_000L, true, participant(null, "P2", warrior, "T1", 0));
        runObjectiveRepository.save(new RunObjective(fastRun, 0, OBJECTIVE_NAME, 2, 0L, 3000L, 3000L, 0));

        mockMvc.perform(get("/api/leaderboards/maps/" + MAP_ID + "/sections/" + OBJECTIVE_NAME).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].duration_ms").value(3000))
                .andExpect(jsonPath("$[0].run_id").value(fastRun.getId()))
                .andExpect(jsonPath("$[1].duration_ms").value(8000));
    }

    @Test
    void sectionIncludesStartDoneOffsetsAndOnlyRoleGatedParticipants() throws Exception {
        MockHttpSession session = signup("sectionparticipants", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        // Only "T1" is gated in for this objective — "spiker" played in the same run but doesn't earn it.
        roleObjectiveRepository.save(new RoleObjective(new RoleObjectiveId(MAP_ID, OBJECTIVE_NAME, "T1")));

        Run run = seedRun(map, 35_000L, true,
                participant(null, "GatedTank", warrior, "T1", 0),
                participant(null, "UngatedSpiker", warrior, "spiker", 1));
        runObjectiveRepository.save(new RunObjective(run, 0, OBJECTIVE_NAME, 2, 1000L, 4000L, 3000L, 0));

        mockMvc.perform(get("/api/leaderboards/maps/" + MAP_ID + "/sections/" + OBJECTIVE_NAME).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].start_ms").value(1000))
                .andExpect(jsonPath("$[0].done_ms").value(4000))
                .andExpect(jsonPath("$[0].participants.length()").value(1))
                .andExpect(jsonPath("$[0].participants[0].raw_name").value("GatedTank"))
                .andExpect(jsonPath("$[0].participants[0].role").value("T1"));
    }

    @Test
    void personalOverallBestIsMinAcrossAllOfThePersonsLinkedCharactersCompletedOnly() throws Exception {
        MockHttpSession session = signup("personalbest", "password123");
        Person person = personRepository.findByUsername("personalbest").orElseThrow();
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();
        PlayerCharacter charA = character(person, "AltOne");
        PlayerCharacter charB = character(person, "AltTwo");

        seedRun(map, 25_000L, true, participant(charA, "AltOne", warrior, "T1", 0));
        seedRun(map, 15_000L, true, participant(charB, "AltTwo", warrior, "T1", 0));
        seedRun(map, 5_000L, false, participant(charA, "AltOne", warrior, "T1", 0)); // incomplete, excluded

        mockMvc.perform(get("/api/leaderboards/me/maps/" + MAP_ID + "/overall").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duration_ms").value(15_000));
    }

    @Test
    void personalOverallBestReturns204WhenNoCompletedRuns() throws Exception {
        MockHttpSession session = signup("nobest", "password123");
        mockMvc.perform(get("/api/leaderboards/me/maps/" + MAP_ID + "/overall").session(session))
                .andExpect(status().isNoContent());
    }

    @Test
    void personalSectionBestIsRoleGated() throws Exception {
        MockHttpSession session = signup("rolegated", "password123");
        Person person = personRepository.findByUsername("rolegated").orElseThrow();
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();
        PlayerCharacter character = character(person, "GatedToon");

        // Only "T1" is a valid role for this objective; "spiker" is not.
        roleObjectiveRepository.save(new RoleObjective(new RoleObjectiveId(MAP_ID, OBJECTIVE_NAME, "T1")));

        Run runAsT1 = seedRun(map, 10_000L, true, participant(character, "GatedToon", warrior, "T1", 0));
        runObjectiveRepository.save(new RunObjective(runAsT1, 0, OBJECTIVE_NAME, 2, 0L, 4000L, 4000L, 0));

        Run runAsSpiker = seedRun(map, 10_000L, true, participant(character, "GatedToon", warrior, "spiker", 0));
        runObjectiveRepository.save(new RunObjective(runAsSpiker, 0, OBJECTIVE_NAME, 2, 0L, 1000L, 1000L, 0));

        // The spiker run has the faster objective time (1000ms) but doesn't count — the participant's
        // role in that run isn't gated in for this objective, per role_objectives.
        mockMvc.perform(get("/api/leaderboards/me/maps/" + MAP_ID + "/sections/" + OBJECTIVE_NAME).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duration_ms").value(4000))
                .andExpect(jsonPath("$.start_ms").value(0))
                .andExpect(jsonPath("$.done_ms").value(4000))
                .andExpect(jsonPath("$.participants.length()").value(1))
                .andExpect(jsonPath("$.participants[0].raw_name").value("GatedToon"))
                .andExpect(jsonPath("$.participants[0].role").value("T1"));
    }

    @Test
    void personalSectionBestIncludesEveryGatedParticipantInTheWinningRunNotJustThePersonsOwn() throws Exception {
        MockHttpSession session = signup("sharedcredit", "password123");
        Person person = personRepository.findByUsername("sharedcredit").orElseThrow();
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();
        PlayerCharacter character = character(person, "MyToon");

        // Both "T1" and "T2" are gated in for this objective.
        roleObjectiveRepository.save(new RoleObjective(new RoleObjectiveId(MAP_ID, OBJECTIVE_NAME, "T1")));
        roleObjectiveRepository.save(new RoleObjective(new RoleObjectiveId(MAP_ID, OBJECTIVE_NAME, "T2")));

        // "OtherPersonsToon" isn't linked to this person's account at all (character = null).
        Run run = seedRun(map, 10_000L, true,
                participant(character, "MyToon", warrior, "T1", 0),
                participant(null, "OtherPersonsToon", warrior, "T2", 1),
                participant(null, "Ungated", warrior, "spiker", 2));
        runObjectiveRepository.save(new RunObjective(run, 0, OBJECTIVE_NAME, 2, 0L, 4000L, 4000L, 0));

        // The winning run is identified via this person's own participation, but credit for it is
        // shared with everyone in that run whose role is gated — "Ungated" (spiker) still excluded.
        mockMvc.perform(get("/api/leaderboards/me/maps/" + MAP_ID + "/sections/" + OBJECTIVE_NAME).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[0].raw_name").value("MyToon"))
                .andExpect(jsonPath("$.participants[1].raw_name").value("OtherPersonsToon"));
    }

    @Test
    void personalSectionBestReturns204WhenNoMatchingRuns() throws Exception {
        MockHttpSession session = signup("nosectionbest", "password123");
        mockMvc.perform(get("/api/leaderboards/me/maps/" + MAP_ID + "/sections/" + OBJECTIVE_NAME).session(session))
                .andExpect(status().isNoContent());
    }

    @Test
    void overallRespectsFromToTimeWindow() throws Exception {
        MockHttpSession session = signup("windowviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        Instant now = Instant.now();
        seedRun(map, now.minusSeconds(3600), 10_000L, true, participant(null, "Recent", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(30 * 24 * 3600), 5_000L, true, participant(null, "Old", warrior, "T1", 0)); // outside window

        mockMvc.perform(get("/api/leaderboards/maps/" + MAP_ID + "/overall").session(session)
                        .param("from", now.minusSeconds(24 * 3600).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].participants[0].raw_name").value("Recent"));
    }

    @Test
    void personalOverallTopRespectsFromToTimeWindow() throws Exception {
        MockHttpSession session = signup("windowpersonal", "password123");
        Person person = personRepository.findByUsername("windowpersonal").orElseThrow();
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();
        PlayerCharacter character = character(person, "WindowToon");

        Instant now = Instant.now();
        seedRun(map, now.minusSeconds(3600), 10_000L, true, participant(character, "WindowToon", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(30 * 24 * 3600), 5_000L, true, participant(character, "WindowToon", warrior, "sos", 0)); // outside window

        mockMvc.perform(get("/api/leaderboards/me/maps/" + MAP_ID + "/overall/top").session(session)
                        .param("from", now.minusSeconds(24 * 3600).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].duration_ms").value(10_000))
                .andExpect(jsonPath("$[0].participants[0].raw_name").value("WindowToon"))
                .andExpect(jsonPath("$[0].participants[0].role").value("T1"));
    }

    @Test
    void sectionRespectsFromToTimeWindow() throws Exception {
        MockHttpSession session = signup("windowsection", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        Instant now = Instant.now();
        Run recentRun = seedRun(map, now.minusSeconds(3600), 40_000L, true, participant(null, "P1", warrior, "T1", 0));
        runObjectiveRepository.save(new RunObjective(recentRun, 0, OBJECTIVE_NAME, 2, 0L, 8000L, 8000L, 0));
        Run oldRun = seedRun(map, now.minusSeconds(30 * 24 * 3600), 35_000L, true, participant(null, "P2", warrior, "T1", 0));
        runObjectiveRepository.save(new RunObjective(oldRun, 0, OBJECTIVE_NAME, 2, 0L, 3000L, 3000L, 0));

        mockMvc.perform(get("/api/leaderboards/maps/" + MAP_ID + "/sections/" + OBJECTIVE_NAME).session(session)
                        .param("from", now.minusSeconds(24 * 3600).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].run_id").value(recentRun.getId()));
    }

}
