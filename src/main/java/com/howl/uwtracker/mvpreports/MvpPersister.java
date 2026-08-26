package com.howl.uwtracker.mvpreports;

import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunMvpAward;
import com.howl.uwtracker.domain.RunParticipant;
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

    public MvpPersister(RunRepository runRepository, RunParticipantRepository runParticipantRepository,
                         RunMvpAwardRepository runMvpAwardRepository) {
        this.runRepository = runRepository;
        this.runParticipantRepository = runParticipantRepository;
        this.runMvpAwardRepository = runMvpAwardRepository;
    }

    /**
     * Every ballot here was already validated against the run's roster and single-select rule at
     * submit time (see {@code MvpReportService.submit}), so this only tallies and writes — no
     * re-validation. Ties for the most common exact ballot are broken by picking uniformly at random
     * among the tied ballots. {@code awarded_by_person_id} is left null on the persisted row: this is
     * a collective outcome of the vote, not any single reporter's own pick.
     */
    @Transactional
    public void persistMajority(Long runId, Collection<MvpBallot> ballots) {
        Map<MvpBallot, Long> counts = ballots.stream().collect(Collectors.groupingBy(b -> b, Collectors.counting()));
        long maxVotes = counts.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        List<MvpBallot> winners = counts.entrySet().stream()
                .filter(e -> e.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .toList();
        MvpBallot winner = winners.size() == 1 ? winners.get(0) : winners.get(ThreadLocalRandom.current().nextInt(winners.size()));

        log.info("persisting majority mvp award for run {}: {} vote(s) for {} ({} distinct ballot(s) cast, {} tied for first)",
                runId, maxVotes, winner, counts.size(), winners.size());

        Run run = runRepository.getReferenceById(runId);
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
        } else if (!winner.roles().isEmpty()) {
            String role = winner.roles().iterator().next();
            RunParticipant participant = runParticipantRepository.findFirstByRun_IdAndRoleOrderByPartyIndexAsc(runId, role)
                    .orElseThrow(() -> new IllegalStateException(
                            "role " + role + " no longer present in run " + runId + " at persist time"));
            runMvpAwardRepository.save(new RunMvpAward(run, participant, null));
        }
        // else: the winning ballot is the "nothing selected" edge case (empty roles, nobody=false) —
        // a legal but empty submission (see ReportRunMvpRequest), nothing to persist for it.
    }
}
