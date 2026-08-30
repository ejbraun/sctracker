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
 * {@code POST /upload-run} for The Fissure of Woe — a 2-person duo whose {@code (34, 2)}
 * {@code map_configs} row carries the {@code primary_profession} role model. See
 * specs/features/fow-and-party-size.md.
 */
class FowDuoUploadIntegrationTest extends AbstractIntegrationTest {

    private static final int RANGER = 2;
    private static final int ASSASSIN = 7;
    private static final int DERVISH = 10;
    private static final int MONK = 3;
    private static final long UTC_START_SECONDS = 1_700_000_500L;
    // Matches FakePluginStorageConfig.FAKE_VERSION — same gate as UploadRunIntegrationTest.
    private static final String CURRENT_PLUGIN_VERSION = "10";

    @BeforeEach
    void seedMap() {
        seedFissureOfWoe();
    }

    private String issueMachineKey(String... registeredNames) throws Exception {
        MockHttpSession session = signup("fow-uploader-" + System.nanoTime(), "password123");
        mockMvc.perform(post("/api/plugin/download").session(session)).andExpect(status().isOk());
        for (String name : registeredNames) {
            mockMvc.perform(post("/api/characters")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreateCharacterRequest(name))))
                    .andExpect(status().isCreated());
        }
        return generateMachineKey(session, "GWToolboxdll");
    }

    private static List<PartyMemberDto> duo() {
        return new ArrayList<>(List.of(
                new PartyMemberDto("FoW Ranger", RANGER, ASSASSIN, true, false, false, 0, null, List.of(), null),
                new PartyMemberDto("FoW Derv", DERVISH, MONK, true, false, false, 0, null, List.of(), null)));
    }

    private static UploadRunRequest request(long utcStartSeconds, List<PartyMemberDto> members) {
        PartyDto party = new PartyDto(utcStartSeconds, FISSURE_OF_WOE_MAP_ID, "FoW Ranger", "victory", members);
        List<ObjectiveDto> objectives = List.of(
                new ObjectiveDto("ToC", 2, 1000L, 5000L, 4000L, 0),
                new ObjectiveDto("Restore", 2, 5000L, 9000L, 4000L, 0),
                new ObjectiveDto("The Hunt", 2, 9000L, 15000L, 6000L, 0));
        ObjectiveSectionDto objective = new ObjectiveSectionDto(
                FISSURE_OF_WOE_MAP_NAME, 555_000L, utcStartSeconds + 2, objectives, 15_000L);
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
    void duoUploadCreatesRunWithPartySizeTwoAndPrimaryProfessionRoles() throws Exception {
        String key = issueMachineKey("FoW Ranger");

        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", CURRENT_PLUGIN_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(UTC_START_SECONDS, duo()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true));

        List<Run> runs = runRepository.findAll();
        assertThat(runs).hasSize(1);
        Run run = runs.get(0);
        assertThat(run.getMap().getId()).isEqualTo(FISSURE_OF_WOE_MAP_ID);
        assertThat(run.getPartySize()).isEqualTo(2);
        assertThat(run.isCompleted()).isTrue();

        List<RunParticipant> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(run.getId());
        assertThat(participants).hasSize(2);
        assertThat(participants.get(0).getRole()).isEqualTo("Ranger");
        assertThat(participants.get(1).getRole()).isEqualTo("Derv");
    }

    @Test
    void rejectsAPartySizeWithNoConfigForThisMap() throws Exception {
        // FoW has (34, 2) and (34, 8) configs — a 3-person party matches neither.
        String key = issueMachineKey("FoW Ranger");
        List<PartyMemberDto> three = duo();
        three.add(new PartyMemberDto("Third Wheel", RANGER, ASSASSIN, true, false, false, 0, null, List.of(), null));

        upload(key, request(UTC_START_SECONDS, three), 400);
        assertThat(runRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsADuoWithNoRegisteredCharacters() throws Exception {
        // minRegisteredFor(2) = 1, so a duo where neither member is registered is rejected.
        String key = issueMachineKey();
        upload(key, request(UTC_START_SECONDS, duo()), 400);
        assertThat(runRepository.findAll()).isEmpty();
    }

    @Test
    void acceptsADuoWithExactlyOneRegisteredCharacter() throws Exception {
        String key = issueMachineKey("FoW Ranger");
        upload(key, request(UTC_START_SECONDS, duo()), 200);
        assertThat(runRepository.findAll()).hasSize(1);
    }

    @Test
    void eightManUploadCreatesRunWithPartySizeEightAndNoRoles() throws Exception {
        // (34, 8) has role_model = NULL — every participant's role stays null (no fixed composition).
        String key = issueMachineKey("FoW 8man 0", "FoW 8man 1", "FoW 8man 2", "FoW 8man 3");
        List<PartyMemberDto> eight = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            eight.add(new PartyMemberDto("FoW 8man " + i, RANGER, ASSASSIN, true, false, false, 0, null, List.of(), null));
        }

        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", CURRENT_PLUGIN_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(UTC_START_SECONDS, eight))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true));

        Run run = runRepository.findAll().get(0);
        assertThat(run.getMap().getId()).isEqualTo(FISSURE_OF_WOE_MAP_ID);
        assertThat(run.getPartySize()).isEqualTo(8);
        assertThat(run.isCompleted()).isTrue();

        List<RunParticipant> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(run.getId());
        assertThat(participants).hasSize(8);
        assertThat(participants).allSatisfy(p -> assertThat(p.getRole()).isNull());
    }

    @Test
    void rejectsAnEightManFowUploadWithTooFewRegisteredCharacters() throws Exception {
        // minRegisteredFor(8) = 4; register only 3.
        String key = issueMachineKey("FoW 8man 0", "FoW 8man 1", "FoW 8man 2");
        List<PartyMemberDto> eight = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            eight.add(new PartyMemberDto("FoW 8man " + i, RANGER, ASSASSIN, true, false, false, 0, null, List.of(), null));
        }
        upload(key, request(UTC_START_SECONDS, eight), 400);
        assertThat(runRepository.findAll()).isEmpty();
    }
}
