package com.howl.uwtracker.failurereports;

import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.failurereports.dto.CanReportFailureResponse;
import com.howl.uwtracker.failurereports.dto.ReportRunFailureRequest;
import com.howl.uwtracker.repository.RunParticipantRepository;
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
    private final RunParticipantRepository runParticipantRepository;
    private final FailureReportVotingRegistry votingRegistry;

    public FailureReportService(MachineKeyAuthenticationService machineKeyAuthenticationService, RunRepository runRepository,
                                 RunParticipantRepository runParticipantRepository,
                                 FailureReportVotingRegistry votingRegistry) {
        this.machineKeyAuthenticationService = machineKeyAuthenticationService;
        this.runRepository = runRepository;
        this.runParticipantRepository = runParticipantRepository;
        this.votingRegistry = votingRegistry;
    }

    /** Called by the plugin on load, before it decides whether to run any failure-report UI/logic at all. */
    public CanReportFailureResponse checkPermission(String rawMachineKey, Integer pluginVersion) {
        Person person = machineKeyAuthenticationService.authenticate(rawMachineKey, pluginVersion);
        return new CanReportFailureResponse(person.isCanReportFailures());
    }

    /**
     * Validates and casts one vote into {@link FailureReportVotingRegistry} — this no longer writes
     * run_failure_reasons directly. The registry holds every vote in memory until the run's 60s
     * voting window (from Run.createdAt) closes, then {@link FailureReportPersister} writes whichever
     * ballot got the most votes. Read-only: the only repository work here is validating the request
     * against the run's existing roster, same queries as before.
     */
    @Transactional(readOnly = true)
    public void submit(String rawMachineKey, Integer pluginVersion, ReportRunFailureRequest request) {
        Person reporter = machineKeyAuthenticationService.authenticate(rawMachineKey, pluginVersion);
        if (!reporter.isCanReportFailures()) {
            log.warn("rejecting failure report: personId={} not permitted to report run failures", reporter.getId());
            throw new ApiException(HttpStatus.FORBIDDEN, "not permitted to report run failures");
        }

        if (request.runId() == null) {
            log.warn("rejecting failure report: missing runId (personId={}, roles={})", reporter.getId(), request.roles());
            throw new ApiException(HttpStatus.BAD_REQUEST, "runId is required");
        }
        Run run = runRepository.findById(request.runId())
                .orElseThrow(() -> {
                    log.warn("rejecting failure report: run not found (personId={}, runId={})", reporter.getId(), request.runId());
                    return new ApiException(HttpStatus.BAD_REQUEST, "run not found");
                });

        List<String> requestedRoles = request.roles() == null ? List.of() : request.roles();
        Set<String> roles = new LinkedHashSet<>(requestedRoles);

        boolean wantsNobody = roles.remove(NOBODY_ROLE);
        if (wantsNobody && !roles.isEmpty()) {
            log.warn("rejecting failure report: Nobody mixed with roles (personId={}, runId={}, roles={})",
                    reporter.getId(), run.getId(), requestedRoles);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nobody is exclusive of specific roles");
        }

        Set<String> rolesInRun = runParticipantRepository.findDistinctRolesByRunId(run.getId());
        for (String role : roles) {
            if (!rolesInRun.contains(role)) {
                log.warn("rejecting failure report: role {} not present in run (personId={}, runId={}, rolesInRun={})",
                        role, reporter.getId(), run.getId(), rolesInRun);
                throw new ApiException(HttpStatus.BAD_REQUEST, "role " + role + " not present in run " + run.getId());
            }
        }

        // May throw a 409 ApiException if this run's voting window has already closed — see
        // FailureReportVotingRegistry#submitVote.
        votingRegistry.submitVote(run.getId(), run.getCreatedAt(), reporter.getId(), new Ballot(wantsNobody, roles));
    }
}
