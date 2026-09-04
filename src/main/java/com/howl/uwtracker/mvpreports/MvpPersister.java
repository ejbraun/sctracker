package com.howl.uwtracker.mvpreports;

import com.howl.uwtracker.domain.MapConfig;
import com.howl.uwtracker.domain.MapConfigId;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunMvpAward;
import com.howl.uwtracker.domain.RunParticipant;
import com.howl.uwtracker.repository.MapConfigRepository;
import com.howl.uwtracker.repository.RunMvpAwardRepository;
import com.howl.uwtracker.repository.RunParticipantRepository;
import com.howl.uwtracker.repository.RunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Writes the winning ballot from a closed {@link MvpVotingRegistry} window as this run's
 * {@code run_mvp_awards} row. Kept as its own bean rather than a method on the registry so
 * {@code @Transactional} actually applies — same reasoning as {@code FailureReportPersister}.
 */
@Component
public class MvpPersister {

    private static final Logger log = LoggerFactory.getLogger(MvpPersister.class);

    private final RunRepository runRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final RunMvpAwardRepository runMvpAwardRepository;
    private final MapConfigRepository mapConfigRepository;

    public MvpPersister(RunRepository runRepository, RunParticipantRepository runParticipantRepository,
                         RunMvpAwardRepository runMvpAwardRepository, MapConfigRepository mapConfigRepository) {
        this.runRepository = runRepository;
        this.runParticipantRepository = runParticipantRepository;
        this.runMvpAwardRepository = runMvpAwardRepository;
        this.mapConfigRepository = mapConfigRepository;
    }

    /**
     * Tallies the closed window's ballots and writes the winner. Ballots are no longer roster-checked
     * at submit time (see {@code MvpReportService.submit}), so that happens here instead: a ballot
     * crediting a target that isn't in the run's roster <em>now</em> — with every party member's
     * upload in and {@code RoleDerivation} done — is dropped before the tally. "Nobody" and the
     * legitimately-empty ballot carry no target and always count. If nothing tallyable survives, any
     * existing award is left untouched (no delete). Ties for the most common surviving ballot are
     * broken by picking uniformly at random among them. {@code awarded_by_person_id} is left null on
     * the persisted row: this is a collective outcome of the vote, not any single reporter's pick.
     *
     * <p>A ballot's targets are matched against {@code role} for a run whose {@code (map,
     * party_size)} config has a role model, or against {@code raw_name} for one that doesn't (see
     * specs/features/fow-and-party-size.md §9.6) — decided from {@link MapConfig#getRoleModel()},
     * not from whether {@code findDistinctRolesByRunId} happens to be empty: an individual
     * participant's role can be null in a role-based run too (an unresolved profession combo), so
     * roster-emptiness alone isn't a safe signal that this run's config is actually role-less.
     */
    @Transactional
    public void persistMajority(Long runId, Collection<MvpBallot> ballots) {
        Run run = runRepository.getReferenceById(runId);
        MapConfig config = mapConfigRepository.findById(new MapConfigId(run.getMap().getId(), run.getPartySize()))
                .orElseThrow(() -> new IllegalStateException("no map_configs row for run " + runId + " at persist time"));
        boolean roleLess = config.getRoleModel() == null;
        Set<String> rosterInRun = roleLess
                ? Set.copyOf(runParticipantRepository.findRawNamesByRunId(runId))
                : runParticipantRepository.findDistinctRolesByRunId(runId);

        List<MvpBallot> tallied = ballots.stream()
                .filter(b -> b.nobody() || b.targets().isEmpty() || rosterInRun.containsAll(b.targets()))
                .toList();
        if (tallied.isEmpty()) {
            log.info("mvp voting window closed for run {}: all {} ballot(s) named a target not in the run; "
                    + "leaving any existing award as-is", runId, ballots.size());
            return;
        }

        Map<MvpBallot, Long> counts = tallied.stream().collect(Collectors.groupingBy(b -> b, Collectors.counting()));
        long maxVotes = counts.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        List<MvpBallot> winners = counts.entrySet().stream()
                .filter(e -> e.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .toList();
        MvpBallot winner = winners.size() == 1 ? winners.get(0) : winners.get(ThreadLocalRandom.current().nextInt(winners.size()));

        log.info("persisting majority mvp award for run {}: {} vote(s) for {} ({} ballot(s) tallied, {} dropped for an "
                        + "off-roster target, {} distinct, {} tied for first)",
                runId, maxVotes, winner, tallied.size(), ballots.size() - tallied.size(), counts.size(), winners.size());

        runMvpAwardRepository.deleteByRun_Id(runId);
        // deleteByRun_Id is a derived delete — Spring Data JPA implements it as entityManager.remove()
        // per matching row (not an immediate bulk DELETE), so it's just a pending action in this
        // transaction's flush queue until now. Hibernate's default flush order runs insertions before
        // deletions, so without this explicit flush, the save() below would race ahead of the delete
        // and collide with run_mvp_awards' UNIQUE(run_id) on a resubmit — confirmed by an integration
        // test hitting exactly that before this line was added.
        runMvpAwardRepository.flush();

        if (winner.nobody()) {
            runMvpAwardRepository.save(new RunMvpAward(run, null, null));
        } else if (!winner.targets().isEmpty()) {
            String target = winner.targets().iterator().next();
            Optional<RunParticipant> participant = roleLess
                    ? runParticipantRepository.findByRun_IdAndRawName(runId, target)
                    : runParticipantRepository.findFirstByRun_IdAndRoleOrderByPartyIndexAsc(runId, target);
            runMvpAwardRepository.save(new RunMvpAward(run, participant.orElseThrow(() -> new IllegalStateException(
                    "target " + target + " no longer present in run " + runId + " at persist time")), null));
        }
        // else: the winning ballot is the "nothing selected" edge case (empty targets, nobody=false)
        // — a legal but empty submission (see ReportRunMvpRequest), nothing to persist for it.
    }
}
