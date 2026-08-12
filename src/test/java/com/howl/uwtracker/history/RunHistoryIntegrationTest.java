package com.howl.uwtracker.history;

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
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * specs/backend/06-run-history.md against real MySQL — including the lazy-loading DTO-mapping path
 * for run detail (objectives/participants touching map/character/profession associations) that
 * IMPLEMENTATION_PROGRESS.md flags as previously unverified against a live database.
 */
class RunHistoryIntegrationTest extends AbstractIntegrationTest {

    private static final int MAP_ID = UNDERWORLD_MAP_ID;
    private static final int OTHER_MAP_ID = 99;

    private Run seedRun(GameMap map, Instant utcStart, boolean completed) {
        return seedRun(map, utcStart, completed, "unknown");
    }

    private Run seedRun(GameMap map, Instant utcStart, boolean completed, String endReason) {
        return runRepository.save(new Run(map, utcStart, 1000L, utcStart, endReason, completed, 20_000L));
    }

    @Test
    void searchFiltersByMap() throws Exception {
        MockHttpSession session = signup("historymap", "password123");
        GameMap mapA = gameMapRepository.getReferenceById(MAP_ID);
        GameMap mapB = gameMapRepository.save(new GameMap(OTHER_MAP_ID));
        seedRun(mapA, Instant.now(), true);
        seedRun(mapB, Instant.now(), true);

        mockMvc.perform(get("/api/runs").session(session).param("map", String.valueOf(MAP_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].map_id").value(MAP_ID));
    }

    @Test
    void searchFiltersByCompleted() throws Exception {
        MockHttpSession session = signup("historycompleted", "password123");
        GameMap map = gameMapRepository.getReferenceById(MAP_ID);
        seedRun(map, Instant.now(), true);
        seedRun(map, Instant.now(), false);

        mockMvc.perform(get("/api/runs").session(session).param("completed", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].completed").value(false));
    }

    @Test
    void searchFiltersByEndReason() throws Exception {
        MockHttpSession session = signup("historyendreason", "password123");
        GameMap map = gameMapRepository.getReferenceById(MAP_ID);
        Run resigned = seedRun(map, Instant.now(), false, "resign");
        seedRun(map, Instant.now(), false, "wipe");
        seedRun(map, Instant.now(), true, "unknown");

        mockMvc.perform(get("/api/runs").session(session).param("end_reason", "resign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].run_id").value(resigned.getId()))
                .andExpect(jsonPath("$.items[0].end_reason").value("resign"));
    }

    @Test
    void searchFiltersByDateRange() throws Exception {
        MockHttpSession session = signup("historydate", "password123");
        GameMap map = gameMapRepository.getReferenceById(MAP_ID);
        Instant now = Instant.now();
        Run tooOld = seedRun(map, now.minus(10, ChronoUnit.DAYS), true);
        Run inRange = seedRun(map, now.minus(1, ChronoUnit.DAYS), true);

        mockMvc.perform(get("/api/runs").session(session)
                        .param("from", now.minus(2, ChronoUnit.DAYS).toString())
                        .param("to", now.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].run_id").value(inRange.getId()));
    }

    @Test
    void searchFiltersByPersonCharacterAndRoleCombined() throws Exception {
        MockHttpSession session = signup("historyperson", "password123");
        Person person = personRepository.findByUsername("historyperson").orElseThrow();
        GameMap map = gameMapRepository.getReferenceById(MAP_ID);
        Profession warrior = professionRepository.findById(1).orElseThrow();
        PlayerCharacter myChar = playerCharacterRepository.save(new PlayerCharacter(person, "MyToon"));

        Run myRun = seedRun(map, Instant.now(), true);
        runParticipantRepository.save(new RunParticipant(myRun, myChar, "MyToon", warrior, null, "T1", 0, true, false, false, 0, 0, null));

        Run otherRun = seedRun(map, Instant.now(), true);
        runParticipantRepository.save(new RunParticipant(otherRun, null, "SomeoneElse", warrior, null, "spiker", 0, true, false, false, 0, 0, null));

        mockMvc.perform(get("/api/runs").session(session).param("person", String.valueOf(person.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].run_id").value(myRun.getId()));

        mockMvc.perform(get("/api/runs").session(session)
                        .param("character", String.valueOf(myChar.getId()))
                        .param("role", "T1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].run_id").value(myRun.getId()));

        // Right character, wrong role -> no match.
        mockMvc.perform(get("/api/runs").session(session)
                        .param("character", String.valueOf(myChar.getId()))
                        .param("role", "spiker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void searchPaginationEnvelopeReflectsPageAndSize() throws Exception {
        MockHttpSession session = signup("historypaging", "password123");
        GameMap map = gameMapRepository.getReferenceById(MAP_ID);
        for (int i = 0; i < 5; i++) {
            seedRun(map, Instant.now(), true);
        }

        mockMvc.perform(get("/api/runs").session(session).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total_elements").value(5))
                .andExpect(jsonPath("$.total_pages").value(3));
    }

    @Test
    void detailReturnsFullNestedRunWithObjectivesAndParticipantsInOrder() throws Exception {
        MockHttpSession session = signup("historydetail", "password123");
        GameMap map = gameMapRepository.getReferenceById(MAP_ID);
        Profession warrior = professionRepository.findById(1).orElseThrow();
        Profession ranger = professionRepository.findById(2).orElseThrow();
        Run run = seedRun(map, Instant.now(), true);

        runObjectiveRepository.save(new RunObjective(run, 1, "Second", 2, 1000L, 2000L, 1000L, 0));
        runObjectiveRepository.save(new RunObjective(run, 0, "First", 2, 0L, 1000L, 1000L, 0));

        runParticipantRepository.save(new RunParticipant(run, null, "SlotTwo", ranger, null, "T2", 1, true, false, false, 0, 0, null));
        runParticipantRepository.save(new RunParticipant(run, null, "SlotOne", warrior, null, "T1", 0, true, false, false, 0, 0, null));

        mockMvc.perform(get("/api/runs/" + run.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run_id").value(run.getId()))
                .andExpect(jsonPath("$.map_id").value(MAP_ID))
                .andExpect(jsonPath("$.objectives.length()").value(2))
                .andExpect(jsonPath("$.objectives[0].name").value("First"))
                .andExpect(jsonPath("$.objectives[1].name").value("Second"))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[0].raw_name").value("SlotOne"))
                .andExpect(jsonPath("$.participants[0].primary_profession").value("Warrior"))
                .andExpect(jsonPath("$.participants[1].raw_name").value("SlotTwo"));
    }

    @Test
    void detailReturns404ForUnknownRun() throws Exception {
        MockHttpSession session = signup("historymissing", "password123");
        mockMvc.perform(get("/api/runs/999999").session(session))
                .andExpect(status().isNotFound());
    }
}
