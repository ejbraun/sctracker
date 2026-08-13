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
import com.howl.uwtracker.domain.RunParticipantItemDrop;
import com.howl.uwtracker.domain.RunParticipantItemDropId;
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
                    blueprint.getPartyIndex(), true, false, false, blueprint.getDeaths(), null));
        }
        return run;
    }

    private RunParticipant participant(PlayerCharacter character, String rawName, Profession profession, String role, int index) {
        return new RunParticipant(null, character, rawName, profession, null, role, index, true, false, false, 0, null);
    }

    private PlayerCharacter character(Person person, String name) {
        return playerCharacterRepository.save(new PlayerCharacter(person, name));
    }

    private Person personEntity(String username) {
        return personRepository.save(new Person(username, "irrelevant-hash"));
    }

    private void seedItemDrop(Run run, String rawName, int itemId, int count) {
        RunParticipant participant = runParticipantRepository.findByRun_IdAndRawName(run.getId(), rawName).orElseThrow();
        runParticipantItemDropRepository.save(new RunParticipantItemDrop(
                new RunParticipantItemDropId(participant.getId(), itemId), count));
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

    @Test
    void longestCompletedStreakFindsEachUsersLongestRunOfConsecutiveCompletions() throws Exception {
        MockHttpSession session = signup("streakviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        Instant now = Instant.now();
        // Alice: complete, FAILED, complete, complete, complete -> longest streak is the trailing 3.
        seedRun(map, now.minusSeconds(500), 10_000L, true, participant(null, "Alice", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(400), 10_000L, false, participant(null, "Alice", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(300), 10_000L, true, participant(null, "Alice", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(200), 10_000L, true, participant(null, "Alice", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(100), 10_000L, true, participant(null, "Alice", warrior, "T1", 0));

        // Bob: two completions in a row -> streak of 2, shorter than Alice's (verifies ranking).
        seedRun(map, now.minusSeconds(500), 10_000L, true, participant(null, "Bob", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(400), 10_000L, true, participant(null, "Bob", warrior, "T1", 0));

        // Carol never completes a run -> has no is_hit=1 island, absent from the results entirely.
        seedRun(map, now.minusSeconds(500), 10_000L, false, participant(null, "Carol", warrior, "T1", 0));

        mockMvc.perform(get("/api/leaderboards/maps/" + MAP_ID + "/streaks/completed").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].user").value("Alice"))
                .andExpect(jsonPath("$[0].streak").value(3))
                .andExpect(jsonPath("$[1].user").value("Bob"))
                .andExpect(jsonPath("$[1].streak").value(2));
    }

    @Test
    void longestCompletedStreakRespectsFromToTimeWindow() throws Exception {
        MockHttpSession session = signup("streakwindow", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        Instant now = Instant.now();
        // A 3-run streak entirely outside the window.
        seedRun(map, now.minusSeconds(30 * 24 * 3600 + 200), 10_000L, true, participant(null, "OldStreaker", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(30 * 24 * 3600 + 100), 10_000L, true, participant(null, "OldStreaker", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(30 * 24 * 3600), 10_000L, true, participant(null, "OldStreaker", warrior, "T1", 0));

        // A 1-run streak inside the window.
        seedRun(map, now.minusSeconds(3600), 10_000L, true, participant(null, "RecentStreaker", warrior, "T1", 0));

        mockMvc.perform(get("/api/leaderboards/maps/" + MAP_ID + "/streaks/completed").session(session)
                        .param("from", now.minusSeconds(24 * 3600).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user").value("RecentStreaker"))
                .andExpect(jsonPath("$[0].streak").value(1));
    }

    @Test
    void sectionStartRanksByFastestArrivalNotFastestClear() throws Exception {
        MockHttpSession session = signup("startviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        // Reaches the objective quickly (start_ms=1000) but takes a long time to clear it once there.
        Run earlyArrival = seedRun(map, 40_000L, true, participant(null, "EarlyArriver", warrior, "T1", 0));
        runObjectiveRepository.save(new RunObjective(earlyArrival, 0, OBJECTIVE_NAME, 2, 1000L, 9000L, 8000L, 0));

        // Reaches the objective later (start_ms=5000) but clears it fast once there.
        Run lateArrival = seedRun(map, 35_000L, true, participant(null, "LateArriver", warrior, "T1", 0));
        runObjectiveRepository.save(new RunObjective(lateArrival, 0, OBJECTIVE_NAME, 2, 5000L, 6000L, 1000L, 0));

        // Duration-ranked (existing Sections): LateArriver's 1000ms clear wins.
        mockMvc.perform(get("/api/leaderboards/maps/" + MAP_ID + "/sections/" + OBJECTIVE_NAME).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].run_id").value(lateArrival.getId()));

        // Start-ranked (new): EarlyArriver's 1000ms arrival wins instead.
        mockMvc.perform(get("/api/leaderboards/maps/" + MAP_ID + "/sections/" + OBJECTIVE_NAME + "/start").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].run_id").value(earlyArrival.getId()))
                .andExpect(jsonPath("$[0].start_ms").value(1000));
    }

    @Test
    void sectionStartIncludesFullPartyNotJustGatedRoles() throws Exception {
        MockHttpSession session = signup("startparticipants", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        // Only "T1" is gated in for this objective, but sectionStart isn't role-gated (unlike
        // sectionIncludesStartDoneOffsetsAndOnlyRoleGatedParticipants above) — both should show.
        roleObjectiveRepository.save(new RoleObjective(new RoleObjectiveId(MAP_ID, OBJECTIVE_NAME, "T1")));

        Run run = seedRun(map, 35_000L, true,
                participant(null, "GatedTank", warrior, "T1", 0),
                participant(null, "UngatedSpiker", warrior, "spiker", 1));
        runObjectiveRepository.save(new RunObjective(run, 0, OBJECTIVE_NAME, 2, 1000L, 4000L, 3000L, 0));

        mockMvc.perform(get("/api/leaderboards/maps/" + MAP_ID + "/sections/" + OBJECTIVE_NAME + "/start").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].participants.length()").value(2));
    }

    @Test
    void personalSectionStartIsNotRoleGated() throws Exception {
        MockHttpSession session = signup("startpersonal", "password123");
        Person person = personRepository.findByUsername("startpersonal").orElseThrow();
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();
        PlayerCharacter character = character(person, "UngatedToon");

        // "T1" is the only gated role for this objective; this person only ever played it as "spiker".
        roleObjectiveRepository.save(new RoleObjective(new RoleObjectiveId(MAP_ID, OBJECTIVE_NAME, "T1")));

        Run run = seedRun(map, 10_000L, true, participant(character, "UngatedToon", warrior, "spiker", 0));
        runObjectiveRepository.save(new RunObjective(run, 0, OBJECTIVE_NAME, 2, 2000L, 5000L, 3000L, 0));

        // Duration-ranked "Yours" excludes this run (role not gated) -> 204.
        mockMvc.perform(get("/api/leaderboards/me/maps/" + MAP_ID + "/sections/" + OBJECTIVE_NAME).session(session))
                .andExpect(status().isNoContent());

        // Start-ranked "Yours" isn't role-gated -> still finds it.
        mockMvc.perform(get("/api/leaderboards/me/maps/" + MAP_ID + "/sections/" + OBJECTIVE_NAME + "/start").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start_ms").value(2000));
    }

    @Test
    void sectionStartRespectsFromToTimeWindow() throws Exception {
        MockHttpSession session = signup("startwindow", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        Instant now = Instant.now();
        Run recentRun = seedRun(map, now.minusSeconds(3600), 40_000L, true, participant(null, "P1", warrior, "T1", 0));
        runObjectiveRepository.save(new RunObjective(recentRun, 0, OBJECTIVE_NAME, 2, 1000L, 8000L, 7000L, 0));
        Run oldRun = seedRun(map, now.minusSeconds(30 * 24 * 3600), 35_000L, true, participant(null, "P2", warrior, "T1", 0));
        runObjectiveRepository.save(new RunObjective(oldRun, 0, OBJECTIVE_NAME, 2, 500L, 3000L, 2500L, 0));

        mockMvc.perform(get("/api/leaderboards/maps/" + MAP_ID + "/sections/" + OBJECTIVE_NAME + "/start").session(session)
                        .param("from", now.minusSeconds(24 * 3600).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].run_id").value(recentRun.getId()));
    }

    @Test
    void luckiestPlayersSumsDropsPerItemPerUserAcrossRuns() throws Exception {
        MockHttpSession session = signup("luckviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        Run run1 = seedRun(map, 10_000L, true,
                participant(null, "Lucky", warrior, "T1", 0),
                participant(null, "Unlucky", warrior, "T2", 1));
        seedItemDrop(run1, "Lucky", 930, 2); // Glob of Ectoplasm
        seedItemDrop(run1, "Unlucky", 930, 1);

        Run run2 = seedRun(map, 10_000L, true, participant(null, "Lucky", warrior, "T1", 0));
        seedItemDrop(run2, "Lucky", 930, 3); // Lucky's total across both runs: 5

        mockMvc.perform(get("/api/leaderboards/maps/" + MAP_ID + "/luckiest-players").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].item_id").value(930))
                .andExpect(jsonPath("$[0].item_name").value("Glob of Ectoplasm"))
                .andExpect(jsonPath("$[0].user").value("Lucky"))
                .andExpect(jsonPath("$[0].total_count").value(5))
                .andExpect(jsonPath("$[0].run_count").value(2))
                .andExpect(jsonPath("$[0].avg_per_run").value(2.5)) // 5 across 2 runs
                .andExpect(jsonPath("$[1].user").value("Unlucky"))
                .andExpect(jsonPath("$[1].total_count").value(1))
                .andExpect(jsonPath("$[1].run_count").value(1))
                .andExpect(jsonPath("$[1].avg_per_run").value(1.0)); // 1 across 1 run
    }

    @Test
    void luckiestPlayersRanksByAveragePerRunNotRawTotal() throws Exception {
        MockHttpSession session = signup("luckavgviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        // Grinder: bigger raw total (6) but spread across 3 runs -> lower average (2.0).
        Run grinderRun1 = seedRun(map, 10_000L, true, participant(null, "Grinder", warrior, "T1", 0));
        seedItemDrop(grinderRun1, "Grinder", 930, 2);
        Run grinderRun2 = seedRun(map, 10_000L, true, participant(null, "Grinder", warrior, "T1", 0));
        seedItemDrop(grinderRun2, "Grinder", 930, 2);
        Run grinderRun3 = seedRun(map, 10_000L, true, participant(null, "Grinder", warrior, "T1", 0));
        seedItemDrop(grinderRun3, "Grinder", 930, 2);

        // Sniper: smaller raw total (3) but in a single run -> higher average (3.0), should rank first.
        Run sniperRun = seedRun(map, 10_000L, true, participant(null, "Sniper", warrior, "T1", 0));
        seedItemDrop(sniperRun, "Sniper", 930, 3);

        mockMvc.perform(get("/api/leaderboards/maps/" + MAP_ID + "/luckiest-players").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user").value("Sniper"))
                .andExpect(jsonPath("$[0].total_count").value(3))
                .andExpect(jsonPath("$[0].run_count").value(1))
                .andExpect(jsonPath("$[0].avg_per_run").value(3.0))
                .andExpect(jsonPath("$[1].user").value("Grinder"))
                .andExpect(jsonPath("$[1].total_count").value(6))
                .andExpect(jsonPath("$[1].run_count").value(3))
                .andExpect(jsonPath("$[1].avg_per_run").value(2.0));
    }

    @Test
    void luckiestPlayersGroupsRowsContiguouslyByItemOrderedById() throws Exception {
        MockHttpSession session = signup("luckgroupviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        Run run = seedRun(map, 10_000L, true, participant(null, "Collector", warrior, "T1", 0));
        seedItemDrop(run, "Collector", 32822, 1); // Mini Dhuum (id 32822)
        seedItemDrop(run, "Collector", 930, 1); // Glob of Ectoplasm (id 930) — lower id, sorts first

        mockMvc.perform(get("/api/leaderboards/maps/" + MAP_ID + "/luckiest-players").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].item_name").value("Glob of Ectoplasm"))
                .andExpect(jsonPath("$[1].item_name").value("Mini Dhuum"));
    }

}
