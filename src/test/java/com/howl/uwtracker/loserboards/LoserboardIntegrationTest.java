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
        Run run = runRepository.save(new Run(map, utcStart, 1000L, utcStart, endReason, completed, durationMs));
        for (RunParticipant blueprint : participants) {
            runParticipantRepository.save(new RunParticipant(run, blueprint.getCharacter(), blueprint.getRawName(),
                    blueprint.getPrimaryProfession(), blueprint.getSecondaryProfession(), blueprint.getRole(),
                    blueprint.getPartyIndex(), true, false, false, blueprint.getDeaths()));
        }
        return run;
    }

    private RunParticipant participant(PlayerCharacter character, String rawName, Profession profession, String role, int index) {
        return participant(character, rawName, profession, role, index, 0);
    }

    private RunParticipant participant(PlayerCharacter character, String rawName, Profession profession, String role, int index, int deaths) {
        return new RunParticipant(null, character, rawName, profession, null, role, index, true, false, false, deaths);
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
    void roleFailsOnlyCountsWipesAtAGatedObjective() throws Exception {
        MockHttpSession session = signup("rolefailviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        // Only "T1" is gated in for this objective.
        roleObjectiveRepository.save(new RoleObjective(new RoleObjectiveId(MAP_ID, OBJECTIVE_NAME, "T1")));

        Run t1Wipe = seedRun(map, 10_000L, false, "wipe", participant(null, "T1er", warrior, "T1", 0));
        runObjectiveRepository.save(new RunObjective(t1Wipe, 0, OBJECTIVE_NAME, 1, 0L, 4000L, 4000L, 0));

        // spiker also wipes on this objective, but spiker isn't gated in for it — doesn't count.
        Run spikerWipe = seedRun(map, 10_000L, false, "wipe", participant(null, "Spikerer", warrior, "spiker", 0));
        runObjectiveRepository.save(new RunObjective(spikerWipe, 0, OBJECTIVE_NAME, 1, 0L, 4000L, 4000L, 0));

        mockMvc.perform(get("/api/loserboards/maps/" + MAP_ID + "/role-fails").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.role=='T1' && @.user=='T1er')].total_runs").value(1))
                .andExpect(jsonPath("$[?(@.role=='T1' && @.user=='T1er')].fails").value(1))
                .andExpect(jsonPath("$[?(@.role=='T1' && @.user=='T1er')].percentage").value(100.0))
                .andExpect(jsonPath("$[?(@.role=='spiker' && @.user=='Spikerer')].total_runs").value(1))
                .andExpect(jsonPath("$[?(@.role=='spiker' && @.user=='Spikerer')].fails").value(0))
                .andExpect(jsonPath("$[?(@.role=='spiker' && @.user=='Spikerer')].percentage").value(0.0));
    }

    @Test
    void roleFailsExcludesResigns() throws Exception {
        MockHttpSession session = signup("resignroleviewer", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        roleObjectiveRepository.save(new RoleObjective(new RoleObjectiveId(MAP_ID, OBJECTIVE_NAME, "T1")));

        // Same shape as a wipe (objective at status=1, gated to T1) but end_reason is "resign" — must not count.
        Run resign = seedRun(map, 10_000L, false, "resign", participant(null, "Resigner", warrior, "T1", 0));
        runObjectiveRepository.save(new RunObjective(resign, 0, OBJECTIVE_NAME, 1, 0L, 4000L, 4000L, 0));

        mockMvc.perform(get("/api/loserboards/maps/" + MAP_ID + "/role-fails").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.role=='T1' && @.user=='Resigner')].total_runs").value(1))
                .andExpect(jsonPath("$[?(@.role=='T1' && @.user=='Resigner')].fails").value(0));
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
    void roleFailsRespectsFromToTimeWindow() throws Exception {
        MockHttpSession session = signup("rolefailwindow", "password123");
        GameMap map = map();
        Profession warrior = professionRepository.findById(1).orElseThrow();

        roleObjectiveRepository.save(new RoleObjective(new RoleObjectiveId(MAP_ID, OBJECTIVE_NAME, "T1")));

        Instant now = Instant.now();
        Run recentWipe = seedRun(map, now.minusSeconds(3600), 10_000L, false, "wipe", participant(null, "Recent", warrior, "T1", 0));
        runObjectiveRepository.save(new RunObjective(recentWipe, 0, OBJECTIVE_NAME, 1, 0L, 4000L, 4000L, 0));

        Run oldWipe = seedRun(map, now.minusSeconds(30 * 24 * 3600), 10_000L, false, "wipe", participant(null, "Old", warrior, "T1", 0));
        runObjectiveRepository.save(new RunObjective(oldWipe, 0, OBJECTIVE_NAME, 1, 0L, 4000L, 4000L, 0));

        mockMvc.perform(get("/api/loserboards/maps/" + MAP_ID + "/role-fails").session(session)
                        .param("from", now.minusSeconds(24 * 3600).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.user=='Recent')].total_runs").value(1))
                .andExpect(jsonPath("$[?(@.user=='Old')]").isEmpty());
    }
}
