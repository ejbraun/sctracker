package com.howl.uwtracker.failurereports;

import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunFailureReason;
import com.howl.uwtracker.domain.RunParticipant;
import com.howl.uwtracker.repository.RunFailureReasonRepository;
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

    public FailureReportPersister(RunRepository runRepository, RunParticipantRepository runParticipantRepository,
                                   RunFailureReasonRepository runFailureReasonRepository) {
        this.runRepository = runRepository;
        this.runParticipantRepository = runParticipantRepository;
        this.runFailureReasonRepository = runFailureReasonRepository;
    }

    /**
     * Every ballot here was already validated against the run's roster at submit time (see
     * {@code FailureReportService.submit}), so this only tallies and writes — no re-validation. Ties
     * for the most common exact ballot are broken by picking uniformly at random among the tied
     * ballots. {@code reported_by_person_id} is left null on the persisted rows: this is a collective
     * outcome of the vote, not any single reporter's own report.
     */
    @Transactional
    public void persistMajority(Long runId, Collection<Ballot> ballots) {
        Map<Ballot, Long> counts = ballots.stream().collect(Collectors.groupingBy(b -> b, Collectors.counting()));
        long maxVotes = counts.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        List<Ballot> winners = counts.entrySet().stream()
                .filter(e -> e.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .toList();
        Ballot winner = winners.size() == 1 ? winners.get(0) : winners.get(ThreadLocalRandom.current().nextInt(winners.size()));

        log.info("persisting majority failure reason for run {}: {} vote(s) for {} ({} distinct ballot(s) cast, {} tied for first)",
                runId, maxVotes, winner, counts.size(), winners.size());

        Run run = runRepository.getReferenceById(runId);
        runFailureReasonRepository.deleteByRun_Id(runId);
        for (String role : winner.roles()) {
            RunParticipant participant = runParticipantRepository.findFirstByRun_IdAndRoleOrderByPartyIndexAsc(runId, role)
                    .orElseThrow(() -> new IllegalStateException(
                            "role " + role + " no longer present in run " + runId + " at persist time"));
            runFailureReasonRepository.save(new RunFailureReason(run, participant, null));
        }
        if (winner.nobody()) {
            runFailureReasonRepository.save(new RunFailureReason(run, null, null));
        }
    }
}
