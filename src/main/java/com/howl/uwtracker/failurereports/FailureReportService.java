package com.howl.uwtracker.failurereports;

import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunFailureReason;
import com.howl.uwtracker.domain.RunFailureReasonId;
import com.howl.uwtracker.failurereports.dto.CanReportFailureResponse;
import com.howl.uwtracker.failurereports.dto.ReportRunFailureRequest;
import com.howl.uwtracker.repository.RunFailureReasonRepository;
import com.howl.uwtracker.repository.RunParticipantRepository;
import com.howl.uwtracker.repository.RunRepository;
import com.howl.uwtracker.web.ApiException;
import com.howl.uwtracker.web.MachineKeyAuthenticationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class FailureReportService {

    private final MachineKeyAuthenticationService machineKeyAuthenticationService;
    private final RunRepository runRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final RunFailureReasonRepository runFailureReasonRepository;

    public FailureReportService(MachineKeyAuthenticationService machineKeyAuthenticationService, RunRepository runRepository,
                                 RunParticipantRepository runParticipantRepository,
                                 RunFailureReasonRepository runFailureReasonRepository) {
        this.machineKeyAuthenticationService = machineKeyAuthenticationService;
        this.runRepository = runRepository;
        this.runParticipantRepository = runParticipantRepository;
        this.runFailureReasonRepository = runFailureReasonRepository;
    }

    /** Called by the plugin on load, before it decides whether to run any failure-report UI/logic at all. */
    public CanReportFailureResponse checkPermission(String rawMachineKey, Integer pluginVersion) {
        Person person = machineKeyAuthenticationService.authenticate(rawMachineKey, pluginVersion);
        return new CanReportFailureResponse(person.isCanReportFailures());
    }

    @Transactional
    public void submit(String rawMachineKey, Integer pluginVersion, ReportRunFailureRequest request) {
        Person reporter = machineKeyAuthenticationService.authenticate(rawMachineKey, pluginVersion);
        if (!reporter.isCanReportFailures()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not permitted to report run failures");
        }

        if (request.runId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "runId is required");
        }
        Run run = runRepository.findById(request.runId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "run not found"));

        List<String> requestedRoles = request.roles() == null ? List.of() : request.roles();
        Set<String> roles = new LinkedHashSet<>(requestedRoles);

        Set<String> rolesInRun = runParticipantRepository.findDistinctRolesByRunId(run.getId());
        for (String role : roles) {
            if (!rolesInRun.contains(role)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "role " + role + " not present in run " + run.getId());
            }
        }

        // Wholesale replace, same idiom as UploadRunWriter#attachItemDrops — a resubmission (e.g.
        // via "Unselect All" then Submit to retract a report) always reflects the latest intent.
        runFailureReasonRepository.deleteById_RunId(run.getId());
        for (String role : roles) {
            runFailureReasonRepository.save(new RunFailureReason(new RunFailureReasonId(run.getId(), role), reporter.getId()));
        }
    }
}
