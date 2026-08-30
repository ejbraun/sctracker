package com.howl.uwtracker.failurereports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory ballot box for run failure reports: votes for a run accumulate here for
 * {@link #VOTING_WINDOW_SECONDS} after the first vote lands (hard-capped at
 * {@link #WINDOW_HARD_CAP_SECONDS} past {@code Run.createdAt} — see {@link #openWindow}), then the
 * window closes and hands off to {@link FailureReportPersister} to write the majority reason. Deliberately
 * in-process, not backed by a table — same "fine at this traffic volume" tradeoff already accepted
 * by {@code MapDedupLock}. A server restart mid-window silently drops any votes still pending for
 * runs currently in flight; every prior vote is only ever held in memory, never durable until the
 * window closes.
 */
@Component
public class FailureReportVotingRegistry {

    private static final Logger log = LoggerFactory.getLogger(FailureReportVotingRegistry.class);
    private static final long VOTING_WINDOW_SECONDS = 60;
    // Absolute ceiling on how long after the run row was created its window can still be open —
    // guards against a stuck or replayed deferred submit reopening voting on a long-finished run and
    // overwriting its result. Comfortably longer than any real gap between a party's staggered
    // zone-outs. See openWindow.
    private static final long WINDOW_HARD_CAP_SECONDS = 600;

    private final TaskScheduler taskScheduler;
    private final FailureReportPersister persister;
    private final ConcurrentHashMap<Long, VotingWindow> windows = new ConcurrentHashMap<>();

    public FailureReportVotingRegistry(TaskScheduler taskScheduler, FailureReportPersister persister) {
        this.taskScheduler = taskScheduler;
        this.persister = persister;
    }

    /**
     * Registers {@code reporter}'s ballot for {@code runId}, opening the run's voting window on its
     * first vote (see {@link #openWindow} for why the window is measured from that first vote, not
     * from {@code runCreatedAt}). A later vote from the same reporter overwrites their earlier one
     * (plain map {@code put}, keyed by person id) — same "latest submission wins" intent the old
     * wholesale-replace endpoint had. Once the window has closed the vote is silently dropped
     * (WARN, no exception): the queue is closed and the majority (if any votes arrived at all) has
     * already been, or is about to be, persisted by the window's own close task, and the plugin's
     * deferred submit shouldn't see a vote bounced back at it.
     */
    public void submitVote(Long runId, Instant runCreatedAt, Long reporterPersonId, Ballot ballot) {
        VotingWindow window = windows.computeIfAbsent(runId, id -> openWindow(id, runCreatedAt));

        if (!Instant.now().isBefore(window.closesAt())) {
            log.info("dropping failure vote for run {} from person {}: voting window already closed", runId, reporterPersonId);
            return;
        }
        window.ballots().put(reporterPersonId, ballot);
    }

    private VotingWindow openWindow(Long runId, Instant runCreatedAt) {
        // Measure the window from the first vote (now), NOT from runCreatedAt. runCreatedAt is
        // stamped by whichever party member's client publishes the run first; with staggered
        // zone-outs a later voter's deferred submit can land well past runCreatedAt + 60s through no
        // fault of their own. Anchoring to the first vote gives every reporter a full
        // VOTING_WINDOW_SECONDS from when voting actually starts. Still hard-capped relative to
        // runCreatedAt so a stuck/replayed submit can't reopen a long-finished run's vote.
        Instant firstVoteWindow = Instant.now().plusSeconds(VOTING_WINDOW_SECONDS);
        Instant hardCap = runCreatedAt.plusSeconds(WINDOW_HARD_CAP_SECONDS);
        Instant closesAt = firstVoteWindow.isBefore(hardCap) ? firstVoteWindow : hardCap;
        VotingWindow window = new VotingWindow(closesAt);
        // A closesAt already in the past (first vote arrived after the hard cap) still schedules
        // validly — Spring runs a past-due Instant immediately, promptly closing this now-empty
        // window right back out; the vote that opened it is dropped by submitVote's own check.
        taskScheduler.schedule(() -> closeWindow(runId), closesAt);
        return window;
    }

    /**
     * Fires once per run, at closesAt. Removing from {@code windows} here (rather than an in-place
     * closed flag) is what makes persistence exactly-once: only the caller that wins the race on
     * {@code remove} gets a non-null window to hand to the persister. A vote landing in the same
     * instant as this removal loses the race in {@link #submitVote} and is rejected instead — an
     * accepted, narrow edge case at this traffic volume (see class doc).
     */
    private void closeWindow(Long runId) {
        VotingWindow window = windows.remove(runId);
        if (window == null) {
            return;
        }
        if (window.ballots().isEmpty()) {
            log.info("voting window closed for run {} with no votes; nothing to persist", runId);
            return;
        }
        persister.persistMajority(runId, window.ballots().values());
    }

    private record VotingWindow(Instant closesAt, ConcurrentHashMap<Long, Ballot> ballots) {
        VotingWindow(Instant closesAt) {
            this(closesAt, new ConcurrentHashMap<>());
        }
    }
}
