package com.howl.uwtracker.failurereports;

import com.howl.uwtracker.failurereports.dto.CanReportFailureResponse;
import com.howl.uwtracker.failurereports.dto.ReportRunFailureRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Machine-key authenticated, same as /upload-run — the plugin calls this right after a failed run's upload confirms. */
@RestController
public class FailureReportController {

    private final FailureReportService failureReportService;

    public FailureReportController(FailureReportService failureReportService) {
        this.failureReportService = failureReportService;
    }

    /** Called by the plugin on load so it can skip its failure-report UI/logic entirely when not permitted. */
    @GetMapping("/can-report-run-failure")
    public ResponseEntity<CanReportFailureResponse> canReportFailure(
            @RequestHeader(value = "X-Machine-Key", required = false) String machineKey) {
        return ResponseEntity.ok(failureReportService.checkPermission(machineKey));
    }

    @PostMapping(value = "/report-run-failure", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> reportFailure(
            @RequestHeader(value = "X-Machine-Key", required = false) String machineKey,
            @RequestBody ReportRunFailureRequest request) {
        failureReportService.submit(machineKey, request);
        return ResponseEntity.noContent().build();
    }
}
