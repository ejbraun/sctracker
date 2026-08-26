package com.howl.uwtracker.ingestion;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.auth.dto.GeneratedMachineKeyResponse;
import com.howl.uwtracker.characters.dto.CreateCharacterRequest;
import com.howl.uwtracker.domain.GameMap;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunObjective;
import com.howl.uwtracker.domain.RunParticipant;
import com.howl.uwtracker.domain.RunParticipantItemDrop;
import com.howl.uwtracker.ingestion.dto.ItemDropDto;
import com.howl.uwtracker.ingestion.dto.ObjectiveDto;
import com.howl.uwtracker.ingestion.dto.ObjectiveSectionDto;
import com.howl.uwtracker.ingestion.dto.PartyDto;
import com.howl.uwtracker.ingestion.dto.PartyMemberDto;
import com.howl.uwtracker.ingestion.dto.UploadRunRequest;
import com.howl.uwtracker.ingestion.dto.UploadRunResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full round trip through {@code POST /upload-run} against a real MySQL (Testcontainers) — the
 * top-priority item flagged in IMPLEMENTATION_PROGRESS.md as never having been exercised end to end.
 * Field values below mirror the real GWToolboxdll payload sample's shape described in
 * specs/backend/00-overview.md's "Timestamps" section and specs/backend/02.
 */
class UploadRunIntegrationTest extends AbstractIntegrationTest {

    private static final int WARRIOR = 1;
    private static final int RANGER = 2;
    private static final int MONK = 3;
    private static final int MESMER = 5;
    private static final int ELEMENTALIST = 6;
    private static final int ASSASSIN = 7;
    private static final int RITUALIST = 8;
    private static final int DERVISH = 10;

    private static final int MAP_ID = 72;
    private static final long UTC_START_SECONDS = 1_700_000_000L;
    private static final long SENTINEL = SentinelMapper.SENTINEL;
    // Matches src/main/resources/static/SCTracker.version.json's "version" — must satisfy
    // PluginVersionMetadataLoader.requireCurrentVersion or every authenticated request here gets a
    // 426 before it even reaches the logic under test. Bump alongside that file if it ever changes.
    private static final String CURRENT_PLUGIN_VERSION = "9";

    // 5, not just MIN_REGISTERED_CHARACTERS's 4: resendWithinWindowButDifferentRosterCreatesASecondRunInstead
    // swaps out one of these names ("T1") for an unregistered one to build a different roster, so
    // registering only the bare minimum would make that upload start failing this check instead of
    // exercising the dedup-mismatch behavior it's actually testing.
    private static final List<String> REGISTERED_CHARACTER_NAMES = List.of("T1", "T2", "T3", "T4", "LT");

    private String issueMachineKey() throws Exception {
        MockHttpSession session = signup("uploader-" + System.nanoTime(), "password123");
        // Marks this person as running the current plugin build, same as a real GWToolboxdll client
        // would have by the time it's actually uploading — otherwise every upload here would hit the
        // outdated-plugin silent-drop path (UploadRunService) instead of exercising ingestion itself.
        mockMvc.perform(post("/api/plugin/download").session(session)).andExpect(status().isOk());
        // UploadRunService now rejects an upload with fewer than MIN_REGISTERED_CHARACTERS registered
        // party members — register enough of validParty()'s names up front so every test here clears
        // that bar by default, same as it already needs a current plugin version to clear the 426 gate.
        // character_name is unique table-wide (spec 04), so a test that calls issueMachineKey() more
        // than once (distinct uploaders, e.g. cross-upload role reconciliation tests) would 409 on the
        // second registration — skip whatever an earlier call in this same test already registered.
        for (String characterName : REGISTERED_CHARACTER_NAMES) {
            if (playerCharacterRepository.existsByCharacterName(characterName)) {
                continue;
            }
            mockMvc.perform(post("/api/characters")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new CreateCharacterRequest(characterName))))
                    .andExpect(status().isCreated());
        }
        return generateMachineKey(session, "GWToolboxdll");
    }

    private static List<PartyMemberDto> validParty() {
        return new ArrayList<>(List.of(
                new PartyMemberDto("T1", RANGER, ASSASSIN, true, false, false, 0, "t1", List.of(), null),
                new PartyMemberDto("T2", RANGER, ASSASSIN, true, false, false, 0, "t2", List.of(), null),
                new PartyMemberDto("T3", RANGER, ASSASSIN, true, false, false, 0, "t3", List.of(), null),
                new PartyMemberDto("T4", MESMER, ELEMENTALIST, true, false, false, 0, null, List.of(), null),
                new PartyMemberDto("LT", MESMER, ASSASSIN, true, false, false, 0, null, List.of(), null),
                new PartyMemberDto("Derv", DERVISH, WARRIOR, true, false, false, 0, null, List.of(), null),
                new PartyMemberDto("SoS", RITUALIST, RANGER, true, false, false, 0, null, List.of(), null),
                new PartyMemberDto("Emo", ELEMENTALIST, MONK, true, false, false, 0, null, List.of(), null)
        ));
    }

    private static UploadRunRequest validRequest(long utcStartSeconds, List<PartyMemberDto> members) {
        return validRequest(utcStartSeconds, "T1", members);
    }

    /**
     * {@code characterName} becomes {@code party.character_name} — the uploader's own character,
     * per real payloads (specs/backend/02). Since RoleDerivation.restrictHintsToSelf now only trusts
     * whichever member's name matches this, tests exercising self-vs-non-self role_hint behavior
     * need to set it explicitly rather than relying on the "T1" default.
     */
    private static UploadRunRequest validRequest(long utcStartSeconds, String characterName, List<PartyMemberDto> members) {
        PartyDto party = new PartyDto(utcStartSeconds, MAP_ID, characterName, "victory", members);
        List<ObjectiveDto> objectives = List.of(
                new ObjectiveDto("Vale", 2, 1000L, 5000L, 4000L, 0),
                new ObjectiveDto("Second Trial", 2, 5000L, SENTINEL, SENTINEL, 0),
                new ObjectiveDto("Final Trial", 2, 9000L, 15000L, 6000L, 1)
        );
        ObjectiveSectionDto objective = new ObjectiveSectionDto(
                "The Underworld", 555_000L, utcStartSeconds + 2, objectives, 20_000L);
        return new UploadRunRequest(party, objective);
    }

    private MvcResult upload(String key, UploadRunRequest request) throws Exception {
        return mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", CURRENT_PLUGIN_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
    }

    @Test
    void validUploadCreatesRunWithObjectivesParticipantsAndRoles() throws Exception {
        String key = issueMachineKey();
        UploadRunRequest request = validRequest(UTC_START_SECONDS, validParty());

        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", CURRENT_PLUGIN_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run_id").exists())
                .andExpect(jsonPath("$.created").value(true));

        List<Run> runs = runRepository.findAll();
        assertThat(runs).hasSize(1);
        Run run = runs.get(0);
        assertThat(run.getMap().getId()).isEqualTo(MAP_ID);
        assertThat(run.isCompleted()).isTrue();
        assertThat(run.getEndReason()).isEqualTo("victory");
        assertThat(run.getDurationMs()).isEqualTo(20_000L);

        List<RunObjective> objectives = runObjectiveRepository.findByRun_IdOrderBySequenceAsc(run.getId());
        assertThat(objectives).hasSize(3);
        assertThat(objectives.get(0).getName()).isEqualTo("Vale");
        assertThat(objectives.get(2).getIndent()).isEqualTo(1);

        List<RunParticipant> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(run.getId());
        assertThat(participants).hasSize(8);
        assertThat(participants.get(0).getRole()).isEqualTo("T1");
        assertThat(participants.get(3).getRole()).isEqualTo("T4");
        assertThat(participants.get(4).getRole()).isEqualTo("LT");
        assertThat(participants.get(5).getRole()).isEqualTo("Derv");
        assertThat(participants.get(6).getRole()).isEqualTo("SoS");
        assertThat(participants.get(7).getRole()).isEqualTo("Emo");
        assertThat(participants.get(0).isPlayer()).isTrue();
        assertThat(participants.get(0).isHero()).isFalse();
    }

    @Test
    void itemDropsArePersistedPerParticipant() throws Exception {
        String key = issueMachineKey();
        List<PartyMemberDto> members = validParty();
        members.set(0, new PartyMemberDto("T1", RANGER, ASSASSIN, true, false, false, 0, "t1",
                List.of(new ItemDropDto(930, 2), new ItemDropDto(32822, 1)), null));
        UploadRunRequest request = validRequest(UTC_START_SECONDS, members);

        upload(key, request);

        Run run = runRepository.findAll().get(0);
        RunParticipant participant = runParticipantRepository.findByRun_IdAndRawName(run.getId(), "T1").orElseThrow();
        List<RunParticipantItemDrop> drops = runParticipantItemDropRepository.findAll().stream()
                .filter(d -> d.getId().getRunParticipantId().equals(participant.getId()))
                .toList();
        assertThat(drops).hasSize(2);
        assertThat(drops).anySatisfy(d -> {
            assertThat(d.getId().getItemId()).isEqualTo(930);
            assertThat(d.getCount()).isEqualTo(2);
        });
        assertThat(drops).anySatisfy(d -> {
            assertThat(d.getId().getItemId()).isEqualTo(32822);
            assertThat(d.getCount()).isEqualTo(1);
        });
    }

    @Test
    void gamblingStoneNetIsPersistedPerParticipant() throws Exception {
        String key = issueMachineKey();
        List<PartyMemberDto> members = validParty();
        members.set(0, new PartyMemberDto("T1", RANGER, ASSASSIN, true, false, false, 0, "t1", List.of(), 7));
        members.set(1, new PartyMemberDto("T2", RANGER, ASSASSIN, true, false, false, 0, "t2", List.of(), -3));
        UploadRunRequest request = validRequest(UTC_START_SECONDS, members);

        upload(key, request);

        Run run = runRepository.findAll().get(0);
        assertThat(runParticipantRepository.findByRun_IdAndRawName(run.getId(), "T1").orElseThrow().getGamblingStoneNet()).isEqualTo(7);
        assertThat(runParticipantRepository.findByRun_IdAndRawName(run.getId(), "T2").orElseThrow().getGamblingStoneNet()).isEqualTo(-3);
    }

    @Test
    void gamblingStoneNetOfExactlyZeroIsCollapsedToNull() throws Exception {
        String key = issueMachineKey();
        List<PartyMemberDto> members = validParty();
        members.set(0, new PartyMemberDto("T1", RANGER, ASSASSIN, true, false, false, 0, "t1", List.of(), 0));
        UploadRunRequest request = validRequest(UTC_START_SECONDS, members);

        upload(key, request);

        Run run = runRepository.findAll().get(0);
        assertThat(runParticipantRepository.findByRun_IdAndRawName(run.getId(), "T1").orElseThrow().getGamblingStoneNet()).isNull();
    }

    @Test
    void unknownTrackedItemIdIsSkippedRatherThanFailingTheUpload() throws Exception {
        String key = issueMachineKey();
        List<PartyMemberDto> members = validParty();
        // 999999 isn't seeded in tracked_items — must not reject the whole upload over one bad row.
        members.set(0, new PartyMemberDto("T1", RANGER, ASSASSIN, true, false, false, 0, "t1",
                List.of(new ItemDropDto(999999, 1)), null));
        UploadRunRequest request = validRequest(UTC_START_SECONDS, members);

        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", CURRENT_PLUGIN_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertThat(runParticipantItemDropRepository.findAll()).isEmpty();
    }

    @Test
    void resendReplacesPreviouslyRecordedItemDropsRatherThanSumming() throws Exception {
        String key = issueMachineKey();
        List<PartyMemberDto> firstMembers = validParty();
        firstMembers.set(0, new PartyMemberDto("T1", RANGER, ASSASSIN, true, false, false, 0, "t1",
                List.of(new ItemDropDto(930, 2)), null));
        upload(key, validRequest(UTC_START_SECONDS, firstMembers));

        // Same run (same utc_start, within the dedup window) resent with a different drop count.
        List<PartyMemberDto> secondMembers = validParty();
        secondMembers.set(0, new PartyMemberDto("T1", RANGER, ASSASSIN, true, false, false, 0, "t1",
                List.of(new ItemDropDto(930, 5)), null));
        upload(key, validRequest(UTC_START_SECONDS, secondMembers));

        assertThat(runRepository.findAll()).hasSize(1);
        Run run = runRepository.findAll().get(0);
        RunParticipant participant = runParticipantRepository.findByRun_IdAndRawName(run.getId(), "T1").orElseThrow();
        List<RunParticipantItemDrop> drops = runParticipantItemDropRepository.findAll().stream()
                .filter(d -> d.getId().getRunParticipantId().equals(participant.getId()))
                .toList();
        assertThat(drops).hasSize(1);
        assertThat(drops.get(0).getCount()).isEqualTo(5);
    }

    @Test
    void selfsRoleHintOverridesPartyPositionButNonSelfHintsAreIgnored() throws Exception {
        String key = issueMachineKey();
        List<PartyMemberDto> members = validParty();
        // "T2" (by name) is self here (see validRequest's characterName below) and claims "t1" via
        // hint - contradicts both its own name and the plain party-order assumption (index 0/1/2 =>
        // T1/T2/T3), and must still be honored since it's a genuine self-report. "T1" (by name) is
        // NOT self and claims "t2" via hint - a stray, untrustworthy value (e.g. an old/misbehaving
        // client still guessing at someone else) that must be ignored even though it's well-formed.
        members.set(0, new PartyMemberDto("T1", RANGER, ASSASSIN, true, false, false, 0, "t2", List.of(), null));
        members.set(1, new PartyMemberDto("T2", RANGER, ASSASSIN, true, false, false, 0, "t1", List.of(), null));
        UploadRunRequest request = validRequest(UTC_START_SECONDS, "T2", members);

        upload(key, request);

        Run run = runRepository.findAll().get(0);
        List<RunParticipant> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(run.getId());
        assertThat(participants.get(1).getRole()).isEqualTo("T1"); // self's hint, honored regardless of position/name
        assertThat(participants.get(0).getRole()).isNull(); // non-self hint, ignored
        assertThat(participants.get(2).getRole()).isNull(); // never hinted at all
    }

    @Test
    void secondUploadersSelfReportDoesNotClobberFirstUploadersRoleAndCompletesElimination() throws Exception {
        String key1 = issueMachineKey();
        String key2 = issueMachineKey();

        // Upload 1, from "T2"'s own client: only T2 (self) carries a real hint - exactly what the
        // new client sends, nobody else even attempts to guess anymore.
        List<PartyMemberDto> firstMembers = validParty();
        firstMembers.set(0, new PartyMemberDto("T1", RANGER, ASSASSIN, true, false, false, 0, null, List.of(), null));
        firstMembers.set(1, new PartyMemberDto("T2", RANGER, ASSASSIN, true, false, false, 0, "t2", List.of(), null));
        firstMembers.set(2, new PartyMemberDto("T3", RANGER, ASSASSIN, true, false, false, 0, null, List.of(), null));
        upload(key1, validRequest(UTC_START_SECONDS, "T2", firstMembers));

        Run run = runRepository.findAll().get(0);
        List<RunParticipant> afterFirst = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(run.getId());
        assertThat(afterFirst.get(1).getRole()).isEqualTo("T2");
        assertThat(afterFirst.get(0).getRole()).isNull();
        assertThat(afterFirst.get(2).getRole()).isNull();

        // Upload 2, a few seconds later (within the dedup window) from a *different* real player's
        // own client: "T3" is self this time, and this upload has no data at all about T2 - its role
        // must not be reset to null just because this particular upload can't see it.
        List<PartyMemberDto> secondMembers = validParty();
        secondMembers.set(0, new PartyMemberDto("T1", RANGER, ASSASSIN, true, false, false, 0, null, List.of(), null));
        secondMembers.set(1, new PartyMemberDto("T2", RANGER, ASSASSIN, true, false, false, 0, null, List.of(), null));
        secondMembers.set(2, new PartyMemberDto("T3", RANGER, ASSASSIN, true, false, false, 0, "t3", List.of(), null));
        upload(key2, validRequest(UTC_START_SECONDS + 2, "T3", secondMembers));

        assertThat(runRepository.findAll()).hasSize(1); // merged into the same run via dedup, not a second one
        List<RunParticipant> afterSecond = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(run.getId());
        assertThat(afterSecond.get(1).getRole()).isEqualTo("T2"); // preserved from upload 1, not clobbered
        assertThat(afterSecond.get(2).getRole()).isEqualTo("T3"); // this upload's own self-report
        assertThat(afterSecond.get(0).getRole()).isEqualTo("T1"); // inferred by elimination: 2 of 3 now known
    }

    @Test
    void strayNonSelfHintIsIgnored() throws Exception {
        String key = issueMachineKey();
        List<PartyMemberDto> members = validParty();
        // "T2" is self and genuinely reports "t2". "T1" is NOT self but still carries a well-formed
        // hint value - simulating an old/misbehaving client that still guesses at another player's
        // role from observed skill casts. It must be ignored rather than trusted.
        members.set(0, new PartyMemberDto("T1", RANGER, ASSASSIN, true, false, false, 0, "t1", List.of(), null));
        members.set(1, new PartyMemberDto("T2", RANGER, ASSASSIN, true, false, false, 0, "t2", List.of(), null));
        members.set(2, new PartyMemberDto("T3", RANGER, ASSASSIN, true, false, false, 0, null, List.of(), null));
        upload(key, validRequest(UTC_START_SECONDS, "T2", members));

        Run run = runRepository.findAll().get(0);
        List<RunParticipant> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(run.getId());
        assertThat(participants.get(1).getRole()).isEqualTo("T2");
        assertThat(participants.get(0).getRole()).isNull();
        assertThat(participants.get(2).getRole()).isNull();
    }

    @Test
    void onlyOneSelfReportLeavesTheOtherTrappersUnknown() throws Exception {
        String key = issueMachineKey();
        List<PartyMemberDto> members = validParty();
        members.set(0, new PartyMemberDto("T1", RANGER, ASSASSIN, true, false, false, 0, null, List.of(), null));
        members.set(1, new PartyMemberDto("T2", RANGER, ASSASSIN, true, false, false, 0, "t2", List.of(), null));
        members.set(2, new PartyMemberDto("T3", RANGER, ASSASSIN, true, false, false, 0, null, List.of(), null));
        upload(key, validRequest(UTC_START_SECONDS, "T2", members));

        // Nobody else's own client has uploaded yet - with only one of three known, elimination must
        // not guess; the other two stay unresolved.
        Run run = runRepository.findAll().get(0);
        List<RunParticipant> participants = runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(run.getId());
        assertThat(participants.get(1).getRole()).isEqualTo("T2");
        assertThat(participants.get(0).getRole()).isNull();
        assertThat(participants.get(2).getRole()).isNull();
    }

    @Test
    void utcStartFieldsAreInterpretedAsEpochSecondsNotMilliseconds() throws Exception {
        String key = issueMachineKey();
        upload(key, validRequest(UTC_START_SECONDS, validParty()));

        Run run = runRepository.findAll().get(0);
        // If utc_start were misread as epoch millis, this would land in 1970, not 2023.
        assertThat(run.getUtcStart().getEpochSecond()).isEqualTo(UTC_START_SECONDS);
        assertThat(run.getObjectiveStart().getEpochSecond()).isEqualTo(UTC_START_SECONDS + 2);
    }

    @Test
    void instanceStartMsIsStoredAsRawOffsetNeverConvertedToATimestamp() throws Exception {
        String key = issueMachineKey();
        upload(key, validRequest(UTC_START_SECONDS, validParty()));

        Run run = runRepository.findAll().get(0);
        assertThat(run.getInstanceStartMs()).isEqualTo(555_000L);
    }

    @Test
    void sentinelValuesAreMappedToNullPerField() throws Exception {
        String key = issueMachineKey();
        upload(key, validRequest(UTC_START_SECONDS, validParty()));

        Run run = runRepository.findAll().get(0);
        List<RunObjective> objectives = runObjectiveRepository.findByRun_IdOrderBySequenceAsc(run.getId());
        RunObjective secondTrial = objectives.get(1);
        // start is a real value, done/duration are the sentinel on this one objective — independent per field.
        assertThat(secondTrial.getStartMs()).isEqualTo(5000L);
        assertThat(secondTrial.getDoneMs()).isNull();
        assertThat(secondTrial.getDurationMs()).isNull();
    }

    @Test
    void runIsAttachedToThePreSeededMap() throws Exception {
        // maps is a curated set seeded by migration (011-seed-supported-maps.xml), not
        // auto-discovered/named from whatever an upload happens to carry.
        String key = issueMachineKey();
        upload(key, validRequest(UTC_START_SECONDS, validParty()));

        GameMap map = gameMapRepository.findById(MAP_ID).orElseThrow();
        assertThat(map.getName()).isEqualTo(UNDERWORLD_MAP_NAME);
    }

    @Test
    void rejectsAnUnsupportedMapId() throws Exception {
        String key = issueMachineKey();
        int unsupportedMapId = MAP_ID + 1;
        PartyDto party = new PartyDto(UTC_START_SECONDS, unsupportedMapId, "T1", "victory", validParty());
        UploadRunRequest request = new UploadRunRequest(party,
                validRequest(UTC_START_SECONDS, validParty()).objective());

        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", CURRENT_PLUGIN_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        assertThat(runRepository.findAll()).isEmpty();
    }

    @Test
    void resendingSameRunWithinDedupWindowUpsertsInsteadOfDuplicating() throws Exception {
        String key = issueMachineKey();
        UploadRunRequest first = validRequest(UTC_START_SECONDS, validParty());

        MvcResult firstResult = upload(key, first);
        assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
        UploadRunResponse firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), UploadRunResponse.class);
        assertThat(firstResponse.created()).isTrue();

        // Resend 2 seconds later — within the 60s dedup window — and expect an upsert, not a duplicate.
        MvcResult secondResult = upload(key, validRequest(UTC_START_SECONDS + 2, validParty()));
        UploadRunResponse secondResponse = objectMapper.readValue(
                secondResult.getResponse().getContentAsString(), UploadRunResponse.class);

        assertThat(secondResponse.created()).isFalse();
        assertThat(secondResponse.runId()).isEqualTo(firstResponse.runId());
        assertThat(runRepository.findAll()).hasSize(1);
        assertThat(runParticipantRepository.findByRun_IdOrderByPartyIndexAsc(firstResponse.runId())).hasSize(8);
    }

    @Test
    void resendOutsideDedupWindowCreatesASecondRun() throws Exception {
        String key = issueMachineKey();
        MvcResult firstResult = upload(key, validRequest(UTC_START_SECONDS, validParty()));
        UploadRunResponse firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), UploadRunResponse.class);

        MvcResult secondResult = upload(key, validRequest(UTC_START_SECONDS + 90, validParty()));
        UploadRunResponse secondResponse = objectMapper.readValue(
                secondResult.getResponse().getContentAsString(), UploadRunResponse.class);

        assertThat(secondResponse.created()).isTrue();
        assertThat(secondResponse.runId()).isNotEqualTo(firstResponse.runId());
        assertThat(runRepository.findAll()).hasSize(2);
    }

    @Test
    void resendWithinWindowButDifferentRosterCreatesASecondRunInstead() throws Exception {
        String key = issueMachineKey();
        MvcResult firstResult = upload(key, validRequest(UTC_START_SECONDS, validParty()));
        UploadRunResponse firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), UploadRunResponse.class);

        List<PartyMemberDto> differentParty = validParty();
        differentParty.set(0, new PartyMemberDto("Someone Else", RANGER, ASSASSIN, true, false, false, 0, "t1", List.of(), null));
        // Within the dedup time window, but a different party entirely (map_id is a global zone id,
        // not tied to any specific server/instance, so two unrelated parties could plausibly start
        // close together) — the roster mismatch should still create a second run, not merge into it.
        MvcResult secondResult = upload(key, validRequest(UTC_START_SECONDS + 10, differentParty));
        UploadRunResponse secondResponse = objectMapper.readValue(
                secondResult.getResponse().getContentAsString(), UploadRunResponse.class);

        assertThat(secondResponse.created()).isTrue();
        assertThat(secondResponse.runId()).isNotEqualTo(firstResponse.runId());
        assertThat(runRepository.findAll()).hasSize(2);
    }

    @Test
    void rejectsMissingObjectiveSection() throws Exception {
        String key = issueMachineKey();
        PartyDto party = new PartyDto(UTC_START_SECONDS, MAP_ID, "T1", "victory", validParty());
        UploadRunRequest request = new UploadRunRequest(party, null);

        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", CURRENT_PLUGIN_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        assertThat(runRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsPartySizeOtherThanEight() throws Exception {
        String key = issueMachineKey();
        List<PartyMemberDto> tooFew = List.of(validParty().get(0));
        UploadRunRequest request = validRequest(UTC_START_SECONDS, tooFew);

        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", CURRENT_PLUGIN_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        assertThat(runRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsUploadWithFewerThanFourRegisteredCharacters() throws Exception {
        String key = issueMachineKey();
        List<PartyMemberDto> party = validParty();
        // issueMachineKey() registers T1/T2/T3/T4/LT; rename all but T1/T2/T3 to unregistered names
        // so this party has only 3 registered characters, one short of the minimum.
        party.set(3, new PartyMemberDto("UnregisteredT4", MESMER, ELEMENTALIST, true, false, false, 0, null, List.of(), null));
        party.set(4, new PartyMemberDto("UnregisteredLT", MESMER, ASSASSIN, true, false, false, 0, null, List.of(), null));
        party.set(5, new PartyMemberDto("UnregisteredDerv", DERVISH, WARRIOR, true, false, false, 0, null, List.of(), null));
        party.set(6, new PartyMemberDto("UnregisteredSoS", RITUALIST, RANGER, true, false, false, 0, null, List.of(), null));
        party.set(7, new PartyMemberDto("UnregisteredEmo", ELEMENTALIST, MONK, true, false, false, 0, null, List.of(), null));
        UploadRunRequest request = validRequest(UTC_START_SECONDS, party);

        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", CURRENT_PLUGIN_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        assertThat(runRepository.findAll()).isEmpty();
    }

    @Test
    void acceptsUploadWithExactlyFourRegisteredCharacters() throws Exception {
        String key = issueMachineKey();
        List<PartyMemberDto> party = validParty();
        // T1/T2/T3/T4 stay registered (exactly the minimum); rename the rest to unregistered names.
        party.set(4, new PartyMemberDto("UnregisteredLT", MESMER, ASSASSIN, true, false, false, 0, null, List.of(), null));
        party.set(5, new PartyMemberDto("UnregisteredDerv", DERVISH, WARRIOR, true, false, false, 0, null, List.of(), null));
        party.set(6, new PartyMemberDto("UnregisteredSoS", RITUALIST, RANGER, true, false, false, 0, null, List.of(), null));
        party.set(7, new PartyMemberDto("UnregisteredEmo", ELEMENTALIST, MONK, true, false, false, 0, null, List.of(), null));
        UploadRunRequest request = validRequest(UTC_START_SECONDS, party);

        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", CURRENT_PLUGIN_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        assertThat(runRepository.findAll()).hasSize(1);
    }

    @Test
    void rejectsMissingMachineKey() throws Exception {
        UploadRunRequest request = validRequest(UTC_START_SECONDS, validParty());
        mockMvc.perform(post("/upload-run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidMachineKey() throws Exception {
        UploadRunRequest request = validRequest(UTC_START_SECONDS, validParty());
        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", "not-a-real-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRevokedMachineKey() throws Exception {
        MockHttpSession session = signup("revoker-" + System.nanoTime(), "password123");
        String body = mockMvc.perform(post("/api/account/machine-keys")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"to-revoke\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        GeneratedMachineKeyResponse generated = objectMapper.readValue(body, GeneratedMachineKeyResponse.class);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/account/machine-keys/" + generated.id())
                        .session(session))
                .andExpect(status().isNoContent());

        UploadRunRequest request = validRequest(UTC_START_SECONDS, validParty());
        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", generated.key())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownProfessionId() throws Exception {
        String key = issueMachineKey();
        List<PartyMemberDto> members = validParty();
        members.set(0, new PartyMemberDto("T1", 999, ASSASSIN, true, false, false, 0, null, List.of(), null));
        UploadRunRequest request = validRequest(UTC_START_SECONDS, members);

        mockMvc.perform(post("/upload-run")
                        .header("X-Machine-Key", key)
                        .header("X-Plugin-Version", CURRENT_PLUGIN_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void incompleteRunWhenLastObjectiveStatusIsNotTwo() throws Exception {
        String key = issueMachineKey();
        PartyDto party = new PartyDto(UTC_START_SECONDS, MAP_ID, "T1", "resign", validParty());
        List<ObjectiveDto> objectives = List.of(
                new ObjectiveDto("Vale", 2, 1000L, 5000L, 4000L, 0),
                new ObjectiveDto("Second Trial", 1, 5000L, null, null, 0)
        );
        ObjectiveSectionDto objective = new ObjectiveSectionDto("The Underworld", 555_000L, UTC_START_SECONDS + 2, objectives, null);
        UploadRunRequest request = new UploadRunRequest(party, objective);

        upload(key, request);

        Run run = runRepository.findAll().get(0);
        assertThat(run.isCompleted()).isFalse();
        assertThat(run.getEndReason()).isEqualTo("resign");
    }
}
