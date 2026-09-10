package com.howl.uwtracker.ingestion;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.characters.dto.CreateCharacterRequest;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunParticipant;
import com.howl.uwtracker.ingestion.dto.ObjectiveDto;
import com.howl.uwtracker.ingestion.dto.ObjectiveSectionDto;
import com.howl.uwtracker.ingestion.dto.PartyDto;
import com.howl.uwtracker.ingestion.dto.PartyMemberDto;
import com.howl.uwtracker.ingestion.dto.UploadRunRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /upload-run} for an Eye of the North dungeon — 8-man, role-less
 * ({@code map_configs.role_model = NULL}), the same shape as Domain of Anguish. A multi-level
 * dungeon run arrives as one payload under the entry-level map id with {@code "Level 1".."Level N"}
 * objectives (GWToolboxdll flattens the levels into one ObjectiveSet). See
 * specs/features/dungeons.md.
 */
class DungeonUploadIntegrationTest extends AbstractIntegrationTest {

    private static final int WARRIOR = 1;
    private static final int MONK = 3;
    private static final long UTC_START_SECONDS = 1_700_000_900L;
    // Matches FakePluginStorageConfig.FAKE_VERSION — same gate as the other upload tests.
    private static final String CURRENT_PLUGIN_VERSION = "10";

    @BeforeEach
    void seedMap() {
        seedDungeons();
    }

    private String issueMachineKey(String... registeredNames) throws Exception {
        MockHttpSession session = signup("dungeon-uploader-" + System.nanoTime(), "password123");
        for (String name : registeredNames) {
            mockMvc.perform(post("/api/characters")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreateCharacterRequest(name))))
                    .andExpect(status().isCreated());
        }
        return generateMachineKey(session, "GWToolboxdll");
    }

    private static List<PartyMemberDto> eightMan() {
        List<PartyMemberDto> members = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            members.add(new PartyMemberDto("Dungeoneer " + i, WARRIOR, MONK, true, false, false, 0, null, List.of(), null));
        }
        return members;
    }

    private static List<PartyMemberDto> party(int size) {
        List<PartyMemberDto> members = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            members.add(new PartyMemberDto("Dungeoneer " + i, WARRIOR, MONK, true, false, false, 0, null, List.of(), null));
        }
        return members;
    }

    /** A 3-level clear ({@code "Level 3"} status 2 = completed) on Cathedral of Flames. */
    private static UploadRunRequest request(long utcStartSeconds, int mapId, List<PartyMemberDto> members) {
        return request(utcStartSeconds, mapId, members, 2);
    }

    private static UploadRunRequest request(long utcStartSeconds, int mapId, List<PartyMemberDto> members,
                                            int lastLevelStatus) {
        PartyDto party = new PartyDto(utcStartSeconds, mapId, "Dungeoneer 0", "completed", members);
        List<ObjectiveDto> objectives = List.of(
                new ObjectiveDto("Level 1", 2, 1000L, 60000L, 59000L, 0),
                new ObjectiveDto("Level 2", 2, 60000L, 120000L, 60000L, 0),
                new ObjectiveDto("Level 3", lastLevelStatus, 120000L,
                        lastLevelStatus == 2 ? 180000L : null,
                        lastLevelStatus == 2 ? 60000L : null, 0));
        ObjectiveSectionDto objective = new ObjectiveSectionDto(
                CATHEDRAL_OF_FLAMES_MAP_NAME, 555_000L, utcStartSeconds + 2, objectives, 180_000L);
        return new UploadRunRequest(party, objective);
    }

    private void upload(String key, UploadRunRequest request, int expectedStatus) throws Exception {
        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", CURRENT_PLUGIN_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void eightManClearCreatesRolelessCompletedRun() throws Exception {
        String key = issueMachineKey("Dungeoneer 0");

        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", CURRENT_PLUGIN_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request(UTC_START_SECONDS, CATHEDRAL_OF_FLAMES_MAP_ID, eightMan()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true));

        List<Run> runs = runRepository.findAll();
        assertThat(runs).hasSize(1);
        Run run = runs.get(0);
        assertThat(run.getMap().getId()).isEqualTo(CATHEDRAL_OF_FLAMES_MAP_ID);
        assertThat(run.getPartySize()).isEqualTo(8);
        assertThat(run.isCompleted()).isTrue();

        List<RunParticipant> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(run.getId());
        assertThat(participants).hasSize(8);
        assertThat(participants).allSatisfy(p -> assertThat(p.getRole()).isNull());
    }

    @Test
    void abandonedRunBeforeTheLastLevelIsNotCompleted() throws Exception {
        String key = issueMachineKey("Dungeoneer 0");
        upload(key, request(UTC_START_SECONDS, CATHEDRAL_OF_FLAMES_MAP_ID, eightMan(), 1), 200);

        Run run = runRepository.findAll().get(0);
        assertThat(run.isCompleted()).isFalse();
    }

    @Test
    void acceptsALowManDungeonRunAtAnySizeOneToEight() throws Exception {
        // Dungeons have a role-less config for every party size 1-8 (all-human parties).
        String key = issueMachineKey("Dungeoneer 0");
        upload(key, request(UTC_START_SECONDS, CATHEDRAL_OF_FLAMES_MAP_ID, party(3)), 200);

        Run run = runRepository.findAll().get(0);
        assertThat(run.getPartySize()).isEqualTo(3);
        assertThat(runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(run.getId()))
                .hasSize(3)
                .allSatisfy(p -> assertThat(p.getRole()).isNull());
    }

    @Test
    void rejectsAPartySizeAboveEight() throws Exception {
        String key = issueMachineKey("Dungeoneer 0");
        upload(key, request(UTC_START_SECONDS, CATHEDRAL_OF_FLAMES_MAP_ID, party(9)), 400);
        assertThat(runRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsAnUnseededDungeonMapId() throws Exception {
        String key = issueMachineKey("Dungeoneer 0");
        // 561 (The_Troubled_Keeper) sits between the Cathedral of Flames levels in the GWCA enum but
        // is not a seeded dungeon — no map_configs row.
        upload(key, request(UTC_START_SECONDS, 561, eightMan()), 400);
        assertThat(runRepository.findAll()).isEmpty();
    }

    @Test
    void acceptsAnEightManDungeonRunWithNoRegisteredCharacters() throws Exception {
        // Dungeons have no registered-character floor (same rule as FoW / DoA). The uploader name
        // ("Nobody Here") matches nobody in the party, so auto-claim doesn't fire either.
        String key = issueMachineKey();
        PartyDto party = new PartyDto(UTC_START_SECONDS, CATHEDRAL_OF_FLAMES_MAP_ID, "Nobody Here", "completed", eightMan());
        UploadRunRequest req = new UploadRunRequest(party, request(UTC_START_SECONDS, CATHEDRAL_OF_FLAMES_MAP_ID, eightMan()).objective());
        upload(key, req, 200);
        assertThat(runRepository.findAll()).hasSize(1);
    }

    @Test
    void secondUploadForTheSameRunDedupsToOneRow() throws Exception {
        String keyA = issueMachineKey("Dungeoneer 0");
        String keyB = issueMachineKey("Dungeoneer 1");

        upload(keyA, request(UTC_START_SECONDS, CATHEDRAL_OF_FLAMES_MAP_ID, eightMan()), 200);
        upload(keyB, request(UTC_START_SECONDS, CATHEDRAL_OF_FLAMES_MAP_ID, eightMan()), 200);

        assertThat(runRepository.findAll()).hasSize(1);
        Run run = runRepository.findAll().get(0);
        assertThat(runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(run.getId())).hasSize(8);
    }
}
