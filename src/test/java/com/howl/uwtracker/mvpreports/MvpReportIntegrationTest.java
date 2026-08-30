package com.howl.uwtracker.mvpreports;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.domain.GameMap;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.Profession;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunMvpAward;
import com.howl.uwtracker.domain.RunParticipant;
import com.howl.uwtracker.mvpreports.dto.ReportRunMvpRequest;
import com.howl.uwtracker.repository.RunMvpAwardRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /report-run-mvp — mirrors UploadRunIntegrationTest's real-MySQL style for the HTTP-level
 * validation path, plus a set of direct {@link MvpPersister} calls for the majority-tally/write
 * path (bypassing MvpVotingRegistry's real 60s window entirely — waiting that out in a test isn't
 * worth it, and persistMajority doesn't care how it's invoked). The registry's own window/close
 * mechanics aren't separately tested here: it's a structural mirror of FailureReportVotingRegistry,
 * which has no test coverage of its own either.
 */
class MvpReportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MvpPersister mvpPersister;

    @Autowired
    private RunMvpAwardRepository runMvpAwardRepository;

    private static final String CURRENT_PLUGIN_VERSION = "10";
    private static final String[] FULL_PARTY_ROLES = {"T1", "T2", "T3", "T4", "LT", "Spiker", "SoS", "Emo"};

    private GameMap map() {
        return gameMapRepository.getReferenceById(UNDERWORLD_MAP_ID);
    }

    /** Signs up, marks the plugin as current (avoids a 426), and sets can_report_failures. */
    private String issueMachineKey(boolean permitted) throws Exception {
        String username = "mvp-reporter-" + System.nanoTime();
        MockHttpSession session = signup(username, "password123");
        mockMvc.perform(post("/api/plugin/download").session(session)).andExpect(status().isOk());

        Person person = personRepository.findByUsername(username).orElseThrow();
        person.setCanReportFailures(permitted);
        personRepository.save(person);

        return generateMachineKey(session, "GWToolboxdll");
    }

    /** 8-slot run with the given roles, party index 0..7 — professions are irrelevant to these tests. */
    private Run seedRun(String... roles) {
        Instant now = Instant.now();
        Run run = runRepository.save(new Run(map(), now, 1000L, now, "victory", true, 10_000L, 8));
        Profession warrior = professionRepository.findById(1).orElseThrow();
        for (int i = 0; i < roles.length; i++) {
            runParticipantRepository.save(new RunParticipant(run, null, "P" + i, warrior, null, roles[i], i, true, false, false, 0, null));
        }
        return run;
    }

    private ResultActions vote(String key, Long runId, List<String> roles) throws Exception {
        return vote(key, runId, roles, CURRENT_PLUGIN_VERSION);
    }

    private ResultActions vote(String key, Long runId, List<String> roles, String pluginVersion) throws Exception {
        String body = objectMapper.writeValueAsString(new ReportRunMvpRequest(runId, roles));
        var request = post("/report-run-mvp").contentType(MediaType.APPLICATION_JSON).content(body);
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
    void acceptsANobodyVote() throws Exception {
        String key = issueMachineKey(true);
        Run run = seedRun(FULL_PARTY_ROLES);

        vote(key, run.getId(), List.of("Nobody")).andExpect(status().isNoContent());
    }

    @Test
    void acceptsAnEmptyRolesVote() throws Exception {
        // The client's Submit button only fires with a selection, but the backend shouldn't assume
        // that's always true — an empty array is a legal (if inert) submission, not a 400.
        String key = issueMachineKey(true);
        Run run = seedRun(FULL_PARTY_ROLES);

        vote(key, run.getId(), List.of()).andExpect(status().isNoContent());
    }

    @Test
    void normalizesMoreThanOneRoleRatherThanRejecting() throws Exception {
        // The client radio group sends one role; a payload that breaks that is normalized (first
        // kept) and still accepted, never 400'd back to the plugin.
        String key = issueMachineKey(true);
        Run run = seedRun(FULL_PARTY_ROLES);

        vote(key, run.getId(), List.of("T1", "Spiker")).andExpect(status().isNoContent());
    }

    @Test
    void acceptsARoleNotPresentInTheRun() throws Exception {
        // No roster check at submit time anymore — "T1" before the other trappers have uploaded is a
        // real case. The role is filtered at window close if it never resolves (see MvpPersister).
        String key = issueMachineKey(true);
        Run run = seedRun(FULL_PARTY_ROLES);

        vote(key, run.getId(), List.of("Necro")).andExpect(status().isNoContent());
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
    void acceptsAVoteWhenTheRunWasCreatedOverAMinuteAgo() throws Exception {
        // The 60s window is measured from the first vote, not from created_at — a party member who
        // zones out (and whose deferred vote fires) well after the first publisher still gets in.
        String key = issueMachineKey(true);
        Run run = seedRun(FULL_PARTY_ROLES);
        jdbcTemplate.update("UPDATE runs SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(180)), run.getId());

        vote(key, run.getId(), List.of("Spiker")).andExpect(status().isNoContent());
    }

    @Test
    void acceptsAndDropsVoteLongAfterTheRunEnded() throws Exception {
        String key = issueMachineKey(true);
        Run run = seedRun(FULL_PARTY_ROLES);
        // The window now opens on the first vote, so a merely-old run still accepts one. Backdate
        // created_at (DB-generated, not settable through the entity) past the 10-minute hard cap so
        // even this first vote finds the window already shut — no need to actually wait it out.
        jdbcTemplate.update("UPDATE runs SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(900)), run.getId());

        // A closed window drops the vote with a WARN — the plugin's deferred submit never sees a 4xx.
        vote(key, run.getId(), List.of("Spiker")).andExpect(status().isNoContent());
    }

    @Test
    void persistMajorityWritesTheWinningRoleParticipant() {
        Run run = seedRun(FULL_PARTY_ROLES);
        RunParticipant spiker = runParticipantRepository.findByRun_IdAndRawName(run.getId(), "P5").orElseThrow();

        mvpPersister.persistMajority(run.getId(), List.of(
                new MvpBallot(false, Set.of("Spiker")),
                new MvpBallot(false, Set.of("Spiker")),
                new MvpBallot(false, Set.of("T1"))));

        List<RunMvpAward> awards = awardsForRun(run.getId());
        assertThat(awards).hasSize(1);
        assertThat(awards.get(0).getRunParticipant().getId()).isEqualTo(spiker.getId());
    }

    @Test
    void persistMajorityWritesANullParticipantWhenNobodyWins() {
        Run run = seedRun(FULL_PARTY_ROLES);

        mvpPersister.persistMajority(run.getId(), List.of(
                new MvpBallot(true, Set.of()),
                new MvpBallot(true, Set.of()),
                new MvpBallot(false, Set.of("T1"))));

        List<RunMvpAward> awards = awardsForRun(run.getId());
        assertThat(awards).hasSize(1);
        assertThat(awards.get(0).getRunParticipant()).isNull();
    }

    @Test
    void persistMajorityWritesNothingWhenTheEmptyBallotWins() {
        Run run = seedRun(FULL_PARTY_ROLES);

        mvpPersister.persistMajority(run.getId(), List.of(new MvpBallot(false, Set.of())));

        assertThat(awardsForRun(run.getId())).isEmpty();
    }

    @Test
    void persistMajorityReplacesThePriorAwardOnResubmit() {
        Run run = seedRun(FULL_PARTY_ROLES);
        RunParticipant spiker = runParticipantRepository.findByRun_IdAndRawName(run.getId(), "P5").orElseThrow();

        mvpPersister.persistMajority(run.getId(), List.of(new MvpBallot(false, Set.of("T1"))));
        mvpPersister.persistMajority(run.getId(), List.of(new MvpBallot(false, Set.of("Spiker"))));

        // .getId() only, not a field like rawName: getRunParticipant() is a lazy proxy and this test
        // (unlike a real request) has no active Hibernate session by the time it runs, matching this
        // app's open-in-view=false — same constraint persistMajorityWritesTheWinningRoleParticipant
        // above already works around the same way.
        List<RunMvpAward> awards = awardsForRun(run.getId());
        assertThat(awards).hasSize(1);
        assertThat(awards.get(0).getRunParticipant().getId()).isEqualTo(spiker.getId());
    }

    @Test
    void persistMajorityDropsBallotsForARoleNotInTheRun() {
        // T1 never resolved for this run (the other trappers never uploaded), so the three T1 votes
        // are dropped at the tally and the two Spiker votes decide it.
        Run run = seedRun("T2", "T3", "T4", "LT", "Spiker", "SoS", "Emo");
        RunParticipant spiker = runParticipantRepository.findByRun_IdAndRawName(run.getId(), "P4").orElseThrow();

        mvpPersister.persistMajority(run.getId(), List.of(
                new MvpBallot(false, Set.of("T1")),
                new MvpBallot(false, Set.of("T1")),
                new MvpBallot(false, Set.of("T1")),
                new MvpBallot(false, Set.of("Spiker")),
                new MvpBallot(false, Set.of("Spiker"))));

        List<RunMvpAward> awards = awardsForRun(run.getId());
        assertThat(awards).hasSize(1);
        assertThat(awards.get(0).getRunParticipant().getId()).isEqualTo(spiker.getId());
    }

    @Test
    void persistMajorityKeepsThePriorAwardWhenEveryBallotIsOffRoster() {
        Run run = seedRun(FULL_PARTY_ROLES);
        RunParticipant spiker = runParticipantRepository.findByRun_IdAndRawName(run.getId(), "P5").orElseThrow();
        mvpPersister.persistMajority(run.getId(), List.of(new MvpBallot(false, Set.of("Spiker"))));

        // Every ballot names a role not in the run — nothing tallyable survives, so the existing
        // award is left as-is rather than cleared.
        mvpPersister.persistMajority(run.getId(), List.of(new MvpBallot(false, Set.of("Necro"))));

        List<RunMvpAward> awards = awardsForRun(run.getId());
        assertThat(awards).hasSize(1);
        assertThat(awards.get(0).getRunParticipant().getId()).isEqualTo(spiker.getId());
    }

    private List<RunMvpAward> awardsForRun(Long runId) {
        return runMvpAwardRepository.findAll().stream()
                .filter(a -> a.getRun().getId().equals(runId))
                .toList();
    }
}
