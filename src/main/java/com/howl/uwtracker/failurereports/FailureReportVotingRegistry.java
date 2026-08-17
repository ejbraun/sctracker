package com.howl.uwtracker.failurereports;

import com.howl.uwtracker.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory ballot box for run failure reports: votes for a run accumulate here for
 * {@link #VOTING_WINDOW_SECONDS} after the run's upload ({@code Run.createdAt}), then the window
 * closes and hands off to {@link FailureReportPersister} to write the majority reason. Deliberately
 * in-process, not backed by a table — same "fine at this traffic volume" tradeoff already accepted
 * by {@code MapDedupLock}. A server restart mid-window silently drops any votes still pending for
 * runs currently in flight; every prior vote is only ever held in memory, never durable until the
 * window closes.
 */
@Component
public class FailureReportVotingRegistry {

    private static final Logger log = LoggerFactory.getLogger(FailureReportVotingRegistry.class);
    private static final long VOTING_WINDOW_SECONDS = 60;

    private final TaskScheduler taskScheduler;
    private final FailureReportPersister persister;
    private final ConcurrentHashMap<Long, VotingWindow> windows = new ConcurrentHashMap<>();

    public FailureReportVotingRegistry(TaskScheduler taskScheduler, FailureReportPersister persister) {
        this.taskScheduler = taskScheduler;
        this.persister = persister;
    }

    /**
     * Registers {@code reporter}'s ballot for {@code runId}, opening the run's voting window on its
     * first vote. A later vote from the same reporter overwrites their earlier one (plain map
     * {@code put}, keyed by person id) — same "latest submission wins" intent the old wholesale-
     * replace endpoint had. Throws a 409 {@link ApiException} once {@code runCreatedAt + 60s} has
     * passed: the queue is closed and the majority (if any votes arrived at all) has already been,
     * or is about to be, persisted by the window's own close task.
     */
    public void submitVote(Long runId, Instant runCreatedAt, Long reporterPersonId, Ballot ballot) {
        VotingWindow window = windows.computeIfAbsent(runId, id -> openWindow(id, runCreatedAt));

        if (!Instant.now().isBefore(window.closesAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "voting closed for run " + runId);
        }
        window.ballots().put(reporterPersonId, ballot);
    }

    private VotingWindow openWindow(Long runId, Instant runCreatedAt) {
        Instant closesAt = runCreatedAt.plusSeconds(VOTING_WINDOW_SECONDS);
        VotingWindow window = new VotingWindow(closesAt);
        // A closesAt already in the past (e.g. this is the very first vote and it arrived well after
        // the window would have elapsed) still schedules validly — Spring runs a past-due Instant
        // immediately, which promptly closes this now-empty window right back out again.
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
