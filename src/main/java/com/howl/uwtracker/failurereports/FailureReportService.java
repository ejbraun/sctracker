package com.howl.uwtracker.failurereports;

import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.failurereports.dto.CanReportFailureResponse;
import com.howl.uwtracker.failurereports.dto.ReportRunFailureRequest;
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
public class FailureReportService {

    private static final Logger log = LoggerFactory.getLogger(FailureReportService.class);

    // Sent as a plain entry in the plugin's roles[] payload, not a separate field — see
    // kFailureReasonRoles/kNobodyReasonIndex in SCTracker.cpp. Mutually exclusive with every real
    // role: it asserts nobody was at fault, distinct from no report being filed at all.
    private static final String NOBODY_ROLE = "Nobody";

    private final MachineKeyAuthenticationService machineKeyAuthenticationService;
    private final RunRepository runRepository;
    private final FailureReportVotingRegistry votingRegistry;

    public FailureReportService(MachineKeyAuthenticationService machineKeyAuthenticationService, RunRepository runRepository,
                                 FailureReportVotingRegistry votingRegistry) {
        this.machineKeyAuthenticationService = machineKeyAuthenticationService;
        this.runRepository = runRepository;
        this.votingRegistry = votingRegistry;
    }

    /** Called by the plugin on load, before it decides whether to run any failure-report UI/logic at all. */
    public CanReportFailureResponse checkPermission(String rawMachineKey, Integer pluginVersion) {
        Person person = machineKeyAuthenticationService.authenticate(rawMachineKey, pluginVersion);
        return new CanReportFailureResponse(person.isCanReportFailures());
    }

    /**
     * Validates and casts one vote into {@link FailureReportVotingRegistry}. Same contract as
     * {@code MvpReportService.submit}: the plugin submits this deferred, after its own
     * {@code /upload-run} hands back a {@code run_id}, with no view of the server-side role roster —
     * so nothing here rejects a vote on run content. Every anomaly short of an auth failure is
     * normalized or dropped with a WARN and still returns 204:
     * <ul>
     *   <li>missing / unknown {@code runId} — dropped.</li>
     *   <li>"Nobody" mixed with specific roles — "Nobody" dropped, the specific roles win.</li>
     *   <li>a blamed role not (yet) in the run's roster — accepted; {@link FailureReportPersister}
     *       strips it at window close against the complete roster.</li>
     *   <li>a closed voting window — dropped in {@link FailureReportVotingRegistry#submitVote}.</li>
     * </ul>
     * Only a bad/missing machine key (401) or a reporter without permission (403) still fails the
     * request. Read-only: the only repository work here is loading the run to open its window.
     *
     * <p>{@code request.roles()} holds role names for a role-based run, or character
     * {@code raw_name}s for a role-less one ({@code map_configs.role_model = NULL}) — this method
     * doesn't need to know which; it just collects the strings into a {@link Ballot}.
     * {@link FailureReportPersister} is what resolves the run's actual config and decides which
     * interpretation applies, at window close.
     */
    @Transactional(readOnly = true)
    public void submit(String rawMachineKey, Integer pluginVersion, ReportRunFailureRequest request) {
        Person reporter = machineKeyAuthenticationService.authenticate(rawMachineKey, pluginVersion);
        if (!reporter.isCanReportFailures()) {
            log.warn("rejecting failure report: personId={} not permitted to report run failures", reporter.getId());
            throw new ApiException(HttpStatus.FORBIDDEN, "not permitted to report run failures");
        }

        if (request.runId() == null) {
            log.warn("dropping failure report: missing runId (personId={}, roles={})", reporter.getId(), request.roles());
            return;
        }
        Run run = runRepository.findById(request.runId()).orElse(null);
        if (run == null) {
            log.warn("dropping failure report: run not found (personId={}, runId={})", reporter.getId(), request.runId());
            return;
        }

        List<String> requestedRoles = request.roles() == null ? List.of() : request.roles();
        Set<String> roles = new LinkedHashSet<>(requestedRoles);

        boolean wantsNobody = roles.remove(NOBODY_ROLE);
        if (wantsNobody && !roles.isEmpty()) {
            log.warn("normalizing failure report: Nobody mixed with roles, dropping Nobody (personId={}, runId={}, roles={})",
                    reporter.getId(), run.getId(), requestedRoles);
            wantsNobody = false;
        }

        // May drop the vote (WARN, no exception) if this run's voting window has already closed —
        // see FailureReportVotingRegistry#submitVote.
        votingRegistry.submitVote(run.getId(), run.getCreatedAt(), reporter.getId(), new Ballot(wantsNobody, roles));
    }
}
