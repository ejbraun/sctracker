package com.howl.uwtracker.failurereports;

import com.howl.uwtracker.domain.MapConfig;
import com.howl.uwtracker.domain.MapConfigId;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunFailureReason;
import com.howl.uwtracker.domain.RunParticipant;
import com.howl.uwtracker.repository.MapConfigRepository;
import com.howl.uwtracker.repository.RunFailureReasonRepository;
import com.howl.uwtracker.repository.RunParticipantRepository;
import com.howl.uwtracker.repository.RunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Writes the winning ballot from a closed {@link FailureReportVotingRegistry} window as this run's
 * {@code run_failure_reasons} rows. Kept as its own bean rather than a method on the registry so
 * {@code @Transactional} actually applies — the registry calls this through DI, not self-invocation,
 * same reasoning as {@code UploadRunWriter} being split out from {@code UploadRunService}.
 */
@Component
public class FailureReportPersister {

    private static final Logger log = LoggerFactory.getLogger(FailureReportPersister.class);

    private final RunRepository runRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final RunFailureReasonRepository runFailureReasonRepository;
    private final MapConfigRepository mapConfigRepository;

    public FailureReportPersister(RunRepository runRepository, RunParticipantRepository runParticipantRepository,
                                   RunFailureReasonRepository runFailureReasonRepository, MapConfigRepository mapConfigRepository) {
        this.runRepository = runRepository;
        this.runParticipantRepository = runParticipantRepository;
        this.runFailureReasonRepository = runFailureReasonRepository;
        this.mapConfigRepository = mapConfigRepository;
    }

    /**
     * Tallies the closed window's ballots and writes the winner. Ballots are no longer roster-checked
     * at submit time (see {@code FailureReportService.submit}), so that happens here: any blamed
     * target not in the run's roster <em>now</em> — every party member's upload in, {@code
     * RoleDerivation} done — is stripped from each ballot before the tally. A ballot left with no
     * targets and no "Nobody" had nothing tallyable and is dropped; "Nobody" ballots always count. If
     * nothing survives, any existing failure rows are left untouched (no delete). Ties for the most
     * common surviving ballot are broken by picking uniformly at random among them.
     * {@code reported_by_person_id} is left null on the persisted rows: this is a collective outcome
     * of the vote, not any single reporter's own report.
     *
     * <p>A ballot's targets are matched against {@code role} for a run whose {@code (map,
     * party_size)} config has a role model, or against {@code raw_name} for one that doesn't (see
     * specs/features/fow-and-party-size.md §9.6) — decided from {@link MapConfig#getRoleModel()},
     * not from whether {@code findDistinctRolesByRunId} happens to be empty: an individual
     * participant's role can be null in a role-based run too (an unresolved profession combo), so
     * roster-emptiness alone isn't a safe signal that this run's config is actually role-less.
     */
    @Transactional
    public void persistMajority(Long runId, Collection<Ballot> ballots) {
        Run run = runRepository.getReferenceById(runId);
        MapConfig config = mapConfigRepository.findById(new MapConfigId(run.getMap().getId(), run.getPartySize()))
                .orElseThrow(() -> new IllegalStateException("no map_configs row for run " + runId + " at persist time"));
        boolean roleLess = config.getRoleModel() == null;
        Set<String> rosterInRun = roleLess
                ? Set.copyOf(runParticipantRepository.findRawNamesByRunId(runId))
                : runParticipantRepository.findDistinctRolesByRunId(runId);

        List<Ballot> tallied = ballots.stream()
                .map(b -> {
                    if (b.nobody() || rosterInRun.containsAll(b.targets())) {
                        return b;
                    }
                    Set<String> kept = b.targets().stream()
                            .filter(rosterInRun::contains)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    return new Ballot(false, kept);
                })
                .filter(b -> b.nobody() || !b.targets().isEmpty())
                .toList();
        if (tallied.isEmpty()) {
            log.info("failure voting window closed for run {}: all {} ballot(s) blamed only targets not in the run; "
                    + "leaving any existing failure rows as-is", runId, ballots.size());
            return;
        }

        Map<Ballot, Long> counts = tallied.stream().collect(Collectors.groupingBy(b -> b, Collectors.counting()));
        long maxVotes = counts.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        List<Ballot> winners = counts.entrySet().stream()
                .filter(e -> e.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .toList();
        Ballot winner = winners.size() == 1 ? winners.get(0) : winners.get(ThreadLocalRandom.current().nextInt(winners.size()));

        log.info("persisting majority failure reason for run {}: {} vote(s) for {} ({} ballot(s) tallied after off-roster "
                        + "targets stripped, {} distinct, {} tied for first)",
                runId, maxVotes, winner, tallied.size(), counts.size(), winners.size());

        runFailureReasonRepository.deleteByRun_Id(runId);
        // deleteByRun_Id is a derived delete — Spring Data JPA implements it as entityManager.remove()
        // per matching row (not an immediate bulk DELETE), so it's just a pending action in this
        // transaction's flush queue until now. Hibernate's default flush order runs insertions before
        // deletions, so without this explicit flush, a save() below would race ahead of the delete and
        // collide with run_failure_reasons' UNIQUE(run_id, run_participant_id) on a resubmit — found
        // via the identical bug in the new sibling MvpPersister, which had a test actually exercising
        // this path; this method had none.
        runFailureReasonRepository.flush();
        for (String target : winner.targets()) {
            Optional<RunParticipant> participant = roleLess
                    ? runParticipantRepository.findByRun_IdAndRawName(runId, target)
                    : runParticipantRepository.findFirstByRun_IdAndRoleOrderByPartyIndexAsc(runId, target);
            runFailureReasonRepository.save(new RunFailureReason(run, participant.orElseThrow(() -> new IllegalStateException(
                    "target " + target + " no longer present in run " + runId + " at persist time")), null));
        }
        if (winner.nobody()) {
            runFailureReasonRepository.save(new RunFailureReason(run, null, null));
        }
    }
}
