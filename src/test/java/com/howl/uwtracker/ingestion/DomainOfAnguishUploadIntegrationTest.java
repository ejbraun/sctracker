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
 * {@code POST /upload-run} for Domain of Anguish — an 8-man-only map whose {@code (474, 8)}
 * {@code map_configs} row has no role model (role-less, like FoW 8-man). Every participant's role
 * stays null and the run is not role-gated. See specs/features/fow-and-party-size.md.
 */
class DomainOfAnguishUploadIntegrationTest extends AbstractIntegrationTest {

    private static final int RANGER = 2;
    private static final int ASSASSIN = 7;
    private static final long UTC_START_SECONDS = 1_700_000_900L;
    // Matches FakePluginStorageConfig.FAKE_VERSION — same gate as UploadRunIntegrationTest.
    private static final String CURRENT_PLUGIN_VERSION = "10";

    @BeforeEach
    void seedMap() {
        seedDomainOfAnguish();
    }

    private String issueMachineKey(String... registeredNames) throws Exception {
        MockHttpSession session = signup("doa-uploader-" + System.nanoTime(), "password123");
        for (String name : registeredNames) {
            mockMvc.perform(post("/api/characters")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreateCharacterRequest(name))))
                    .andExpect(status().isCreated());
        }
        return generateMachineKey(session, "GWToolboxdll");
    }

    /** A DoA party of 8 real players. Member 0 is "DoA Runner 0" (the uploader). */
    private static List<PartyMemberDto> eight() {
        List<PartyMemberDto> members = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            members.add(new PartyMemberDto("DoA Runner " + i, RANGER, ASSASSIN, true, false, false, 0, null, List.of(), null));
        }
        return members;
    }

    private static UploadRunRequest request(long utcStartSeconds, List<PartyMemberDto> members) {
        PartyDto party = new PartyDto(utcStartSeconds, DOMAIN_OF_ANGUISH_MAP_ID, "DoA Runner 0", "victory", members);
        // status 2 = Completed (see the plugin's kObjectiveStatusCompleted), matching the FoW tests.
        List<ObjectiveDto> objectives = List.of(
                new ObjectiveDto("Foundry", 2, 1000L, 5000L, 4000L, 0),
                new ObjectiveDto("City", 2, 5000L, 9000L, 4000L, 0),
                new ObjectiveDto("Veil", 2, 9000L, 13000L, 4000L, 0),
                new ObjectiveDto("Gloom", 2, 13000L, 18000L, 5000L, 0));
        ObjectiveSectionDto objective = new ObjectiveSectionDto(
                DOMAIN_OF_ANGUISH_MAP_NAME, 900_000L, utcStartSeconds + 2, objectives, 18_000L);
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
    void eightManUploadCreatesRunWithPartySizeEightAndNoRoles() throws Exception {
        // (474, 8) has role_model = NULL — every participant's role stays null (no fixed composition).
        String key = issueMachineKey("DoA Runner 0", "DoA Runner 1", "DoA Runner 2", "DoA Runner 3");

        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", CURRENT_PLUGIN_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(UTC_START_SECONDS, eight()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true));

        List<Run> runs = runRepository.findAll();
        assertThat(runs).hasSize(1);
        Run run = runs.get(0);
        assertThat(run.getMap().getId()).isEqualTo(DOMAIN_OF_ANGUISH_MAP_ID);
        assertThat(run.getPartySize()).isEqualTo(8);
        assertThat(run.isCompleted()).isTrue();

        List<RunParticipant> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(run.getId());
        assertThat(participants).hasSize(8);
        assertThat(participants).allSatisfy(p -> assertThat(p.getRole()).isNull());
    }

    @Test
    void rejectsAnEightManDoaUploadWithTooFewRegisteredCharacters() throws Exception {
        // minRegisteredFor(8) = 4; register only 3.
        String key = issueMachineKey("DoA Runner 0", "DoA Runner 1", "DoA Runner 2");
        upload(key, request(UTC_START_SECONDS, eight()), 400);
        assertThat(runRepository.findAll()).isEmpty();
    }
}
