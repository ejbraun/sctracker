package com.howl.uwtracker.mvpreports;

import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.mvpreports.dto.ReportRunMvpRequest;
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
public class MvpReportService {

    private static final Logger log = LoggerFactory.getLogger(MvpReportService.class);

    // Sent as a plain entry in roles[], not a separate field — mirrors failurereports' NOBODY_ROLE.
    private static final String NOBODY_ROLE = "Nobody";

    // The client's radio-button group already enforces this, but that's a UI convention, not a
    // guarantee about what actually reaches this endpoint — enforced server-side same as everything
    // else here (run-roster membership, permission).
    private static final int MAX_SELECTED_ROLES = 1;

    private final MachineKeyAuthenticationService machineKeyAuthenticationService;
    private final RunRepository runRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final MvpVotingRegistry votingRegistry;

    public MvpReportService(MachineKeyAuthenticationService machineKeyAuthenticationService, RunRepository runRepository,
                             RunParticipantRepository runParticipantRepository, MvpVotingRegistry votingRegistry) {
        this.machineKeyAuthenticationService = machineKeyAuthenticationService;
        this.runRepository = runRepository;
        this.runParticipantRepository = runParticipantRepository;
        this.votingRegistry = votingRegistry;
    }

    /**
     * Validates and casts one vote into {@link MvpVotingRegistry} — same shape as
     * {@code FailureReportService.submit}, minus the failure endpoint's own can-I-do-this
     * pre-check (there's no {@code GET /can-report-run-mvp}; MVP reuses
     * {@code Person.isCanReportFailures()} directly, same permission as failure reports). Read-only:
     * the only repository work here is validating the request against the run's existing roster.
     */
    @Transactional(readOnly = true)
    public void submit(String rawMachineKey, Integer pluginVersion, ReportRunMvpRequest request) {
        Person reporter = machineKeyAuthenticationService.authenticate(rawMachineKey, pluginVersion);
        if (!reporter.isCanReportFailures()) {
            log.warn("rejecting mvp report: personId={} not permitted to report run mvp", reporter.getId());
            throw new ApiException(HttpStatus.FORBIDDEN, "not permitted to report run mvp");
        }

        if (request.runId() == null) {
            log.warn("rejecting mvp report: missing runId (personId={}, roles={})", reporter.getId(), request.roles());
            throw new ApiException(HttpStatus.BAD_REQUEST, "runId is required");
        }
        Run run = runRepository.findById(request.runId())
                .orElseThrow(() -> {
                    log.warn("rejecting mvp report: run not found (personId={}, runId={})", reporter.getId(), request.runId());
                    return new ApiException(HttpStatus.BAD_REQUEST, "run not found");
                });

        List<String> requestedRoles = request.roles() == null ? List.of() : request.roles();
        if (requestedRoles.size() > MAX_SELECTED_ROLES) {
            log.warn("rejecting mvp report: {} roles submitted, at most {} allowed (personId={}, runId={}, roles={})",
                    requestedRoles.size(), MAX_SELECTED_ROLES, reporter.getId(), run.getId(), requestedRoles);
            throw new ApiException(HttpStatus.BAD_REQUEST, "at most " + MAX_SELECTED_ROLES + " role may be selected for mvp");
        }
        Set<String> roles = new LinkedHashSet<>(requestedRoles);
        boolean wantsNobody = roles.remove(NOBODY_ROLE);

        Set<String> rolesInRun = runParticipantRepository.findDistinctRolesByRunId(run.getId());
        for (String role : roles) {
            if (!rolesInRun.contains(role)) {
                log.warn("rejecting mvp report: role {} not present in run (personId={}, runId={}, rolesInRun={})",
                        role, reporter.getId(), run.getId(), rolesInRun);
                throw new ApiException(HttpStatus.BAD_REQUEST, "role " + role + " not present in run " + run.getId());
            }
        }

        // May throw a 409 ApiException if this run's voting window has already closed — see
        // MvpVotingRegistry#submitVote.
        votingRegistry.submitVote(run.getId(), run.getCreatedAt(), reporter.getId(), new MvpBallot(wantsNobody, roles));
    }
}
