package com.howl.uwtracker.mvpreports;

import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.mvpreports.dto.ReportRunMvpRequest;
import com.howl.uwtracker.repository.RunRepository;
import com.howl.uwtracker.web.ApiException;
import com.howl.uwtracker.web.MachineKeyAuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class MvpReportService {

    private static final Logger log = LoggerFactory.getLogger(MvpReportService.class);

    // Sent as a plain entry in roles[], not a separate field — mirrors failurereports' NOBODY_ROLE.
    private static final String NOBODY_ROLE = "Nobody";

    // The client's radio-button group already enforces this. A payload that breaks it is normalized
    // (first role kept, rest dropped) rather than rejected — this endpoint never 4xx's a well-formed
    // POST back to the plugin over vote content; see the class-level note below.
    private static final int MAX_SELECTED_ROLES = 1;

    private final MachineKeyAuthenticationService machineKeyAuthenticationService;
    private final RunRepository runRepository;
    private final MvpVotingRegistry votingRegistry;

    public MvpReportService(MachineKeyAuthenticationService machineKeyAuthenticationService, RunRepository runRepository,
                             MvpVotingRegistry votingRegistry) {
        this.machineKeyAuthenticationService = machineKeyAuthenticationService;
        this.runRepository = runRepository;
        this.votingRegistry = votingRegistry;
    }

    /**
     * Validates and casts one vote into {@link MvpVotingRegistry}. The plugin fires this as a
     * deferred submit once its own {@code /upload-run} confirms and hands back a {@code run_id}
     * (see SCTracker's {@code FireVoteSubmit}) — by design it can't know the server-side role
     * roster at that point, so nothing here rejects a vote on run content. Every anomaly short of
     * an auth failure is normalized or dropped with a WARN and still returns 204:
     * <ul>
     *   <li>missing / unknown {@code runId} — dropped (the plugin never does this; it only submits
     *       with a {@code run_id} the server itself just issued).</li>
     *   <li>more than one role — first kept, rest dropped.</li>
     *   <li>a role not (yet) in the run's roster — accepted as-is; {@link MvpPersister} filters it
     *       at window close against the by-then-complete roster, so a "T1" vote cast before the
     *       other trappers' uploads let {@code RoleDerivation} resolve T1 still counts once it does.</li>
     *   <li>a closed voting window — dropped in {@link MvpVotingRegistry#submitVote}.</li>
     * </ul>
     * Only a bad/missing machine key (401) or a reporter without permission (403) still fails the
     * request. Read-only: the only repository work here is loading the run to open its window.
     */
    @Transactional(readOnly = true)
    public void submit(String rawMachineKey, Integer pluginVersion, ReportRunMvpRequest request) {
        Person reporter = machineKeyAuthenticationService.authenticate(rawMachineKey, pluginVersion);
        if (!reporter.isCanReportFailures()) {
            log.warn("rejecting mvp report: personId={} not permitted to report run mvp", reporter.getId());
            throw new ApiException(HttpStatus.FORBIDDEN, "not permitted to report run mvp");
        }

        if (request.runId() == null) {
            log.warn("dropping mvp report: missing runId (personId={}, roles={})", reporter.getId(), request.roles());
            return;
        }
        Run run = runRepository.findById(request.runId()).orElse(null);
        if (run == null) {
            log.warn("dropping mvp report: run not found (personId={}, runId={})", reporter.getId(), request.runId());
            return;
        }

        List<String> requestedRoles = request.roles() == null ? List.of() : request.roles();
        Set<String> roles = new LinkedHashSet<>(requestedRoles);
        boolean wantsNobody = roles.remove(NOBODY_ROLE);

        if (roles.size() > MAX_SELECTED_ROLES) {
            String kept = roles.iterator().next();
            log.warn("normalizing mvp report: {} roles submitted, keeping only '{}' (personId={}, runId={}, roles={})",
                    roles.size(), kept, reporter.getId(), run.getId(), requestedRoles);
            roles.retainAll(Set.of(kept));
        }

        // May drop the vote (WARN, no exception) if this run's voting window has already closed —
        // see MvpVotingRegistry#submitVote.
        votingRegistry.submitVote(run.getId(), run.getCreatedAt(), reporter.getId(), new MvpBallot(wantsNobody, roles));
    }
}
