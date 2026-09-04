package com.howl.uwtracker.failurereports;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.domain.GameMap;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.Profession;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunFailureReason;
import com.howl.uwtracker.domain.RunParticipant;
import com.howl.uwtracker.failurereports.dto.ReportRunFailureRequest;
import com.howl.uwtracker.repository.RunFailureReasonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /report-run-failure — no dedicated test file existed for this endpoint before (only
 * {@code GET /can-report-run-failure} got incidental coverage elsewhere, as an authenticated
 * heartbeat in an unrelated plugin test), despite {@link FailureReportPersister}'s own doc comment
 * flagging the gap. Mirrors {@code MvpReportIntegrationTest}'s style: HTTP-level submit validation,
 * plus direct {@link FailureReportPersister} calls for the majority-tally/write path (bypassing
 * {@link FailureReportVotingRegistry}'s real 60s window).
 */
class FailureReportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FailureReportPersister failureReportPersister;

    @Autowired
    private RunFailureReasonRepository runFailureReasonRepository;

    private static final String CURRENT_PLUGIN_VERSION = "10";
    private static final String[] FULL_PARTY_ROLES = {"T1", "T2", "T3", "T4", "LT", "Spiker", "SoS", "Emo"};

    private GameMap map() {
        return gameMapRepository.getReferenceById(UNDERWORLD_MAP_ID);
    }

    private GameMap mapDoa() {
        return gameMapRepository.getReferenceById(DOMAIN_OF_ANGUISH_MAP_ID);
    }

    private String issueMachineKey(boolean permitted) throws Exception {
        String username = "failure-reporter-" + System.nanoTime();
        MockHttpSession session = signup(username, "password123");

        Person person = personRepository.findByUsername(username).orElseThrow();
        person.setCanReportFailures(permitted);
        personRepository.save(person);

        return generateMachineKey(session, "GWToolboxdll");
    }

    /** 8-slot run with the given roles, party index 0..7 — professions are irrelevant to these tests. */
    private Run seedRun(String... roles) {
        Instant now = Instant.now();
        Run run = runRepository.save(new Run(map(), now, 1000L, now, "wipe", false, 10_000L, 8));
        Profession warrior = professionRepository.findById(1).orElseThrow();
        for (int i = 0; i < roles.length; i++) {
            runParticipantRepository.save(new RunParticipant(run, null, "P" + i, warrior, null, roles[i], i, true, false, false, 0, null));
        }
        return run;
    }

    /**
     * An 8-slot Domain of Anguish run (role_model = NULL, per {@code seedDomainOfAnguish()}) —
     * every participant's role stays null, so a name-mode vote (targets are raw names, not roles)
     * is what {@link FailureReportPersister} must resolve against. Callers must call
     * {@link #seedDomainOfAnguish()} first.
     */
    private Run seedRoleLessRun() {
        Instant now = Instant.now();
        Run run = runRepository.save(new Run(mapDoa(), now, 1000L, now, "wipe", false, 10_000L, 8));
        Profession warrior = professionRepository.findById(1).orElseThrow();
        for (int i = 0; i < 8; i++) {
            runParticipantRepository.save(new RunParticipant(run, null, "P" + i, warrior, null, null, i, true, false, false, 0, null));
        }
        return run;
    }

    /**
     * An 8-slot Underworld (trapper-model, role-based) run where every entry still failed to
     * resolve a role — the edge case {@link FailureReportPersister} must not mistake for a
     * role-less config (role-less-ness comes from {@code MapConfig.roleModel}, not roster
     * emptiness).
     */
    private Run seedRoleBasedRunWithNoResolvedRoles() {
        Instant now = Instant.now();
        Run run = runRepository.save(new Run(map(), now, 1000L, now, "wipe", false, 10_000L, 8));
        Profession warrior = professionRepository.findById(1).orElseThrow();
        for (int i = 0; i < 8; i++) {
            runParticipantRepository.save(new RunParticipant(run, null, "P" + i, warrior, null, null, i, true, false, false, 0, null));
        }
        return run;
    }

    private ResultActions vote(String key, Long runId, List<String> roles) throws Exception {
        return vote(key, runId, roles, CURRENT_PLUGIN_VERSION);
    }

    private ResultActions vote(String key, Long runId, List<String> roles, String pluginVersion) throws Exception {
        String body = objectMapper.writeValueAsString(new ReportRunFailureRequest(runId, roles));
        var request = post("/report-run-failure").contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request = request.header("X-Machine-Key", key);
        }
        if (pluginVersion != null) {
            request = request.header("X-Plugin-Version", pluginVersion);
        }
        return mockMvc.perform(request);
    }

    @Test
    void acceptsASingleRoleVote() throws Exception {
        String key = issueMachineKey(true);
        Run run = seedRun(FULL_PARTY_ROLES);

        vote(key, run.getId(), List.of("Spiker")).andExpect(status().isNoContent());
    }

    @Test
    void acceptsAMultiRoleVote() throws Exception {
        // Unlike MVP, failure/blame voting is genuinely multi-select — several roles can share
        // blame for the same wipe.
        String key = issueMachineKey(true);
        Run run = seedRun(FULL_PARTY_ROLES);

        vote(key, run.getId(), List.of("T1", "Spiker")).andExpect(status().isNoContent());
    }

    @Test
    void acceptsANobodyVote() throws Exception {
        String key = issueMachineKey(true);
        Run run = seedRun(FULL_PARTY_ROLES);

        vote(key, run.getId(), List.of("Nobody")).andExpect(status().isNoContent());
    }

    @Test
    void acceptsAndDropsMissingRunId() throws Exception {
        String key = issueMachineKey(true);

        vote(key, null, List.of("Spiker")).andExpect(status().isNoContent());
    }

    @Test
    void acceptsAndDropsUnknownRunId() throws Exception {
        String key = issueMachineKey(true);

        vote(key, 999_999L, List.of("Spiker")).andExpect(status().isNoContent());
    }

    @Test
    void rejectsWhenNotPermitted() throws Exception {
        String key = issueMachineKey(false);
        Run run = seedRun(FULL_PARTY_ROLES);

        vote(key, run.getId(), List.of("Spiker")).andExpect(status().isForbidden());
    }

    @Test
    void rejectsMissingMachineKey() throws Exception {
        Run run = seedRun(FULL_PARTY_ROLES);

        vote(null, run.getId(), List.of("Spiker")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsOutdatedPluginVersion() throws Exception {
        String key = issueMachineKey(true);
        Run run = seedRun(FULL_PARTY_ROLES);

        vote(key, run.getId(), List.of("Spiker"), "1").andExpect(status().is(426));
    }

    @Test
    void acceptsANameVoteForARoleLessRun() throws Exception {
        seedDomainOfAnguish();
        String key = issueMachineKey(true);
        Run run = seedRoleLessRun();

        vote(key, run.getId(), List.of("P5")).andExpect(status().isNoContent());
    }

    @Test
    void persistMajorityWritesTheWinningRoleParticipant() {
        Run run = seedRun(FULL_PARTY_ROLES);
        RunParticipant spiker = runParticipantRepository.findByRun_IdAndRawName(run.getId(), "P5").orElseThrow();

        failureReportPersister.persistMajority(run.getId(), List.of(
                new Ballot(false, Set.of("Spiker")),
                new Ballot(false, Set.of("Spiker")),
                new Ballot(false, Set.of("T1"))));

        List<RunFailureReason> reasons = runFailureReasonRepository.findByRun_Id(run.getId());
        assertThat(reasons).hasSize(1);
        assertThat(reasons.get(0).getRunParticipant().getId()).isEqualTo(spiker.getId());
    }

    @Test
    void persistMajorityWritesMultipleRoleParticipantsForATiedMultiRoleBallot() {
        Run run = seedRun(FULL_PARTY_ROLES);
        RunParticipant t1 = runParticipantRepository.findByRun_IdAndRawName(run.getId(), "P0").orElseThrow();
        RunParticipant spiker = runParticipantRepository.findByRun_IdAndRawName(run.getId(), "P5").orElseThrow();

        failureReportPersister.persistMajority(run.getId(), List.of(
                new Ballot(false, Set.of("T1", "Spiker")),
                new Ballot(false, Set.of("T1", "Spiker"))));

        List<RunFailureReason> reasons = runFailureReasonRepository.findByRun_Id(run.getId());
        assertThat(reasons).hasSize(2);
        assertThat(reasons.stream().map(r -> r.getRunParticipant().getId()))
                .containsExactlyInAnyOrder(t1.getId(), spiker.getId());
    }

    @Test
    void persistMajorityWritesANullParticipantWhenNobodyWins() {
        Run run = seedRun(FULL_PARTY_ROLES);

        failureReportPersister.persistMajority(run.getId(), List.of(
                new Ballot(true, Set.of()),
                new Ballot(true, Set.of()),
                new Ballot(false, Set.of("T1"))));

        List<RunFailureReason> reasons = runFailureReasonRepository.findByRun_Id(run.getId());
        assertThat(reasons).hasSize(1);
        assertThat(reasons.get(0).getRunParticipant()).isNull();
    }

    @Test
    void persistMajorityDropsBallotsForARoleNotInTheRun() {
        Run run = seedRun("T2", "T3", "T4", "LT", "Spiker", "SoS", "Emo");
        RunParticipant spiker = runParticipantRepository.findByRun_IdAndRawName(run.getId(), "P4").orElseThrow();

        failureReportPersister.persistMajority(run.getId(), List.of(
                new Ballot(false, Set.of("T1")),
                new Ballot(false, Set.of("T1")),
                new Ballot(false, Set.of("Spiker")),
                new Ballot(false, Set.of("Spiker"))));

        List<RunFailureReason> reasons = runFailureReasonRepository.findByRun_Id(run.getId());
        assertThat(reasons).hasSize(1);
        assertThat(reasons.get(0).getRunParticipant().getId()).isEqualTo(spiker.getId());
    }

    @Test
    void persistMajorityWritesTheWinningCharacterParticipantForARoleLessRun() {
        seedDomainOfAnguish();
        Run run = seedRoleLessRun();
        RunParticipant p5 = runParticipantRepository.findByRun_IdAndRawName(run.getId(), "P5").orElseThrow();

        failureReportPersister.persistMajority(run.getId(), List.of(
                new Ballot(false, Set.of("P5")),
                new Ballot(false, Set.of("P5")),
                new Ballot(false, Set.of("P1"))));

        List<RunFailureReason> reasons = runFailureReasonRepository.findByRun_Id(run.getId());
        assertThat(reasons).hasSize(1);
        assertThat(reasons.get(0).getRunParticipant().getId()).isEqualTo(p5.getId());
    }

    @Test
    void persistMajorityDropsBallotsForANameNotInTheRoleLessRun() {
        seedDomainOfAnguish();
        Run run = seedRoleLessRun();
        RunParticipant p5 = runParticipantRepository.findByRun_IdAndRawName(run.getId(), "P5").orElseThrow();

        failureReportPersister.persistMajority(run.getId(), List.of(
                new Ballot(false, Set.of("Nonexistent Character")),
                new Ballot(false, Set.of("P5"))));

        List<RunFailureReason> reasons = runFailureReasonRepository.findByRun_Id(run.getId());
        assertThat(reasons).hasSize(1);
        assertThat(reasons.get(0).getRunParticipant().getId()).isEqualTo(p5.getId());
    }

    @Test
    void persistMajorityDoesNotSwitchToNameModeForARoleBasedRunWithNoResolvedRoles() {
        // Every participant's role is null here too, but the (map, party_size) config itself is
        // TRAPPER, not NULL — FailureReportPersister must still match against role (an empty
        // roster), not fall back to raw_name matching just because no role happened to resolve. A
        // ballot naming a raw name ("P5") is therefore off-roster and gets stripped to empty, and
        // dropped entirely (not "Nobody").
        Run run = seedRoleBasedRunWithNoResolvedRoles();

        failureReportPersister.persistMajority(run.getId(), List.of(new Ballot(false, Set.of("P5"))));

        assertThat(runFailureReasonRepository.findByRun_Id(run.getId())).isEmpty();
    }
}
