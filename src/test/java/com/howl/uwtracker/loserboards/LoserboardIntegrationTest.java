package com.howl.uwtracker.loserboards;

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

/** "Loserboards" against real MySQL — mirrors LeaderboardIntegrationTest's setup style. */
class LoserboardIntegrationTest extends AbstractIntegrationTest {

    private static final int MAP_ID = UNDERWORLD_MAP_ID;
    private static final String OBJECTIVE_NAME = "The Vale";

    /** Pre-seeded by 011-seed-supported-maps.xml (reset before every test — see AbstractIntegrationTest). */
    private GameMap map() {
        return gameMapRepository.getReferenceById(MAP_ID);
    }

    private Run seedRun(GameMap map, long durationMs, boolean completed, String endReason, RunParticipant... participants) {
        return seedRun(map, Instant.now(), durationMs, completed, endReason, participants);
    }

    private Run seedRun(GameMap map, Instant utcStart, long durationMs, boolean completed, String endReason, RunParticipant... participants) {
        Run run = runRepository.save(new Run(map, utcStart, 1000L, utcStart, endReason, completed, durationMs, 8));
        for (RunParticipant blueprint : participants) {
            runParticipantRepository.save(new RunParticipant(run, blueprint.getCharacter(), blueprint.getRawName(),
                    blueprint.getPrimaryProfession(), blueprint.getSecondaryProfession(), blueprint.getRole(),
                    blueprint.getPartyIndex(), true, false, false, blueprint.getDeaths(), null));
        }
        return run;
    }

    private RunParticipant participant(PlayerCharacter character, String rawName, Profession profession, String role, int index) {
        return participant(character, rawName, profession, role, index, 0);
    }

    private RunParticipant participant(PlayerCharacter character, String rawName, Profession profession, String role, int index, int deaths) {
        return new RunParticipant(null, character, rawName, profession, null, role, index, true, false, false, deaths, null);
    }

    private PlayerCharacter character(Person person, String name) {
        return playerCharacterRepository.save(new PlayerCharacter(person, name));
    }

    private Person personEntity(String username) {
        return personRepository.save(new Person(username, "irrelevant-hash"));
    }

    @Test
    void worstReturnsCompletedRunsSlowestFirst() throws Exception {
        MockHttpSession session = signup("loserviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        seedRun(map, 30_000L, true, "unknown", participant(null, "Slow", warrior, "T1", 0));
        seedRun(map, 10_000L, true, "unknown", participant(null, "Fast", warrior, "T1", 0));
        seedRun(map, 20_000L, true, "unknown", participant(null, "Medium", warrior, "T1", 0));
        seedRun(map, 99_000L, false, "wipe", participant(null, "Incomplete", warrior, "T1", 0)); // excluded

        mockMvc.perform(get("/api/loserboards/maps/" + MAP_ID + "/worst").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].duration_ms").value(30_000))
                .andExpect(jsonPath("$[0].participants[0].raw_name").value("Slow"))
                .andExpect(jsonPath("$[1].duration_ms").value(20_000))
                .andExpect(jsonPath("$[2].duration_ms").value(10_000));
    }

    @Test
    void roleDeathsSumsPerRolePerUserRegardlessOfCompletion() throws Exception {
        MockHttpSession session = signup("deathsviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        seedRun(map, 10_000L, true, "unknown", participant(null, "Careful", warrior, "T1", 0, 1));
        seedRun(map, 10_000L, false, "wipe", participant(null, "Careful", warrior, "T1", 0, 3)); // not completed — still counts
        seedRun(map, 10_000L, true, "unknown", participant(null, "Reckless", warrior, "T2", 0, 5));

        mockMvc.perform(get("/api/loserboards/maps/" + MAP_ID + "/role-deaths").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.role=='T1' && @.user=='Careful')].total_runs").value(2))
                .andExpect(jsonPath("$[?(@.role=='T1' && @.user=='Careful')].deaths").value(4))
                .andExpect(jsonPath("$[?(@.role=='T1' && @.user=='Careful')].avg_deaths").value(2.0))
                .andExpect(jsonPath("$[?(@.role=='T2' && @.user=='Reckless')].total_runs").value(1))
                .andExpect(jsonPath("$[?(@.role=='T2' && @.user=='Reckless')].deaths").value(5))
                .andExpect(jsonPath("$[?(@.role=='T2' && @.user=='Reckless')].avg_deaths").value(5.0));
    }

    @Test
    void roleDeathsOrdersByDeathsPerRunNotRawTotal() throws Exception {
        MockHttpSession session = signup("deathsrateviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        // FewButFatal: 1 run, 5 deaths -> 5.0/run. ManyButSafe: 10 runs, 20 deaths total -> 2.0/run.
        // Raw total favors ManyButSafe (20 > 5); rate favors FewButFatal (5.0 > 2.0) — must sort by rate.
        seedRun(map, 10_000L, true, "unknown", participant(null, "FewButFatal", warrior, "T1", 0, 5));
        for (int i = 0; i < 10; i++) {
            seedRun(map, 10_000L, true, "unknown", participant(null, "ManyButSafe", warrior, "T1", 0, 2));
        }

        mockMvc.perform(get("/api/loserboards/maps/" + MAP_ID + "/role-deaths").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user").value("FewButFatal"))
                .andExpect(jsonPath("$[0].avg_deaths").value(5.0))
                .andExpect(jsonPath("$[1].user").value("ManyButSafe"))
                .andExpect(jsonPath("$[1].avg_deaths").value(2.0));
    }

    @Test
    void globalFailsCountsResignsPerUser() throws Exception {
        MockHttpSession session = signup("globalfailviewer", "password123");
        Person person = personEntity("resignprone");
        PlayerCharacter toon = character(person, "ResignToon");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        seedRun(map, 5_000L, false, "resign", participant(toon, "ResignToon", warrior, "T1", 0));
        seedRun(map, 5_000L, false, "resign", participant(toon, "ResignToon", warrior, "T1", 0));
        seedRun(map, 5_000L, false, "wipe", participant(toon, "ResignToon", warrior, "T1", 0));
        seedRun(map, 10_000L, true, "unknown", participant(toon, "ResignToon", warrior, "T1", 0));

        // "user" falls back to raw_name (character name) since this test person never set an alias.
        mockMvc.perform(get("/api/loserboards/maps/" + MAP_ID + "/global-fails").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.user=='ResignToon')].total_runs").value(4))
                .andExpect(jsonPath("$[?(@.user=='ResignToon')].resigns").value(2))
                .andExpect(jsonPath("$[?(@.user=='ResignToon')].percentage").value(50.0));
    }

    @Test
    void globalFailsOrdersByResignPercentageNotRawCount() throws Exception {
        MockHttpSession session = signup("resignrateviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        // SingleResigner: 1 run, 1 resign -> 100%. FrequentRunner: 4 runs, 2 resigns -> 50%.
        // Raw resign count favors FrequentRunner (2 > 1); percentage favors SingleResigner (100 > 50).
        seedRun(map, 5_000L, false, "resign", participant(null, "SingleResigner", warrior, "T1", 0));

        seedRun(map, 5_000L, false, "resign", participant(null, "FrequentRunner", warrior, "T1", 0));
        seedRun(map, 5_000L, false, "resign", participant(null, "FrequentRunner", warrior, "T1", 0));
        seedRun(map, 10_000L, true, "unknown", participant(null, "FrequentRunner", warrior, "T1", 0));
        seedRun(map, 10_000L, true, "unknown", participant(null, "FrequentRunner", warrior, "T1", 0));

        mockMvc.perform(get("/api/loserboards/maps/" + MAP_ID + "/global-fails").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user").value("SingleResigner"))
                .andExpect(jsonPath("$[0].percentage").value(100.0))
                .andExpect(jsonPath("$[1].user").value("FrequentRunner"))
                .andExpect(jsonPath("$[1].percentage").value(50.0));
    }

    @Test
    void globalFailsExcludesWipes() throws Exception {
        MockHttpSession session = signup("globalwipeviewer", "password123");
        Person person = personEntity("wipeprone");
        PlayerCharacter toon = character(person, "WipeToon");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        seedRun(map, 5_000L, false, "wipe", participant(toon, "WipeToon", warrior, "T1", 0));

        mockMvc.perform(get("/api/loserboards/maps/" + MAP_ID + "/global-fails").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.user=='WipeToon')].total_runs").value(1))
                .andExpect(jsonPath("$[?(@.user=='WipeToon')].resigns").value(0));
    }

    @Test
    void longestBadStreakFindsLongestRunOfConsecutiveResignsAndWipesCombined() throws Exception {
        MockHttpSession session = signup("badstreakviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        Instant now = Instant.now();
        // Dave: wipe, resign, COMPLETED, wipe, resign, wipe -> longest bad streak is the trailing 3
        // (resign/wipe share one "bad outcome" category; the completed run in the middle breaks it).
        seedRun(map, now.minusSeconds(600), 10_000L, false, "wipe", participant(null, "Dave", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(500), 10_000L, false, "resign", participant(null, "Dave", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(400), 10_000L, true, "unknown", participant(null, "Dave", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(300), 10_000L, false, "wipe", participant(null, "Dave", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(200), 10_000L, false, "resign", participant(null, "Dave", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(100), 10_000L, false, "wipe", participant(null, "Dave", warrior, "T1", 0));

        // Eve: two bad runs in a row -> streak of 2, shorter than Dave's (verifies ranking).
        seedRun(map, now.minusSeconds(500), 10_000L, false, "wipe", participant(null, "Eve", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(400), 10_000L, false, "resign", participant(null, "Eve", warrior, "T1", 0));

        // Frank never has a bad run -> has no is_hit=1 island, absent from the results entirely.
        seedRun(map, now.minusSeconds(500), 10_000L, true, "unknown", participant(null, "Frank", warrior, "T1", 0));

        mockMvc.perform(get("/api/loserboards/maps/" + MAP_ID + "/streaks/bad").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].user").value("Dave"))
                .andExpect(jsonPath("$[0].streak").value(3))
                .andExpect(jsonPath("$[1].user").value("Eve"))
                .andExpect(jsonPath("$[1].streak").value(2));
    }

    @Test
    void longestBadStreakUnknownEndReasonBreaksTheStreak() throws Exception {
        MockHttpSession session = signup("unknownbreak", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        Instant now = Instant.now();
        // wipe, wipe, UNKNOWN (not completed, but not resign/wipe either), wipe -> two separate
        // streaks of 2, not one streak of 3 — "unknown" is its own island, not a "bad" hit.
        seedRun(map, now.minusSeconds(400), 10_000L, false, "wipe", participant(null, "Between", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(300), 10_000L, false, "wipe", participant(null, "Between", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(200), 10_000L, false, "unknown", participant(null, "Between", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(100), 10_000L, false, "wipe", participant(null, "Between", warrior, "T1", 0));

        mockMvc.perform(get("/api/loserboards/maps/" + MAP_ID + "/streaks/bad").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.user=='Between')].streak").value(2));
    }

    @Test
    void longestBadStreakRespectsFromToTimeWindow() throws Exception {
        MockHttpSession session = signup("badstreakwindow", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        Instant now = Instant.now();
        seedRun(map, now.minusSeconds(30 * 24 * 3600 + 200), 10_000L, false, "wipe", participant(null, "OldLoser", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(30 * 24 * 3600 + 100), 10_000L, false, "wipe", participant(null, "OldLoser", warrior, "T1", 0));
        seedRun(map, now.minusSeconds(30 * 24 * 3600), 10_000L, false, "wipe", participant(null, "OldLoser", warrior, "T1", 0));

        seedRun(map, now.minusSeconds(3600), 10_000L, false, "resign", participant(null, "RecentLoser", warrior, "T1", 0));

        mockMvc.perform(get("/api/loserboards/maps/" + MAP_ID + "/streaks/bad").session(session)
                        .param("from", now.minusSeconds(24 * 3600).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user").value("RecentLoser"))
                .andExpect(jsonPath("$[0].streak").value(1));
    }

    @Test
    void sectionSlowestStartRanksBySlowestArrival() throws Exception {
        MockHttpSession session = signup("slowstartviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        Run earlyArrival = seedRun(map, 40_000L, true, "unknown", participant(null, "EarlyArriver", warrior, "T1", 0));
        runObjectiveRepository.save(new RunObjective(earlyArrival, 0, OBJECTIVE_NAME, 2, 1000L, 9000L, 8000L, 0));

        Run lateArrival = seedRun(map, 35_000L, true, "unknown", participant(null, "LateArriver", warrior, "T1", 0));
        runObjectiveRepository.save(new RunObjective(lateArrival, 0, OBJECTIVE_NAME, 2, 5000L, 6000L, 1000L, 0));

        mockMvc.perform(get("/api/loserboards/maps/" + MAP_ID + "/sections/" + OBJECTIVE_NAME + "/start").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].run_id").value(lateArrival.getId()))
                .andExpect(jsonPath("$[0].start_ms").value(5000));
    }

    @Test
    void sectionSlowestStartIncludesOnlyRoleGatedParticipants() throws Exception {
        MockHttpSession session = signup("slowstartparticipants", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        roleObjectiveRepository.save(new RoleObjective(new RoleObjectiveId(MAP_ID, OBJECTIVE_NAME, "T1")));

        Run run = seedRun(map, 35_000L, true, "unknown",
                participant(null, "GatedTank", warrior, "T1", 0),
                participant(null, "UngatedSpiker", warrior, "spiker", 1));
        runObjectiveRepository.save(new RunObjective(run, 0, OBJECTIVE_NAME, 2, 1000L, 4000L, 3000L, 0));

        mockMvc.perform(get("/api/loserboards/maps/" + MAP_ID + "/sections/" + OBJECTIVE_NAME + "/start").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].participants.length()").value(1))
                .andExpect(jsonPath("$[0].participants[0].raw_name").value("GatedTank"));
    }
}
