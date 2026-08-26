package com.howl.uwtracker.mvpreports;

import com.howl.uwtracker.mvpreports.dto.ReportRunMvpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Machine-key authenticated, same as /upload-run and /report-run-failure — the plugin calls this
 * after a successful run's upload confirms, crediting the party's standout role. No paired
 * {@code GET /can-report-run-mvp}: this reuses /can-report-run-failure's existing permission check
 * (same {@code Person.isCanReportFailures()} flag), so the plugin already knows whether to show
 * either popup from that one call.
 */
@RestController
public class MvpReportController {

    private final MvpReportService mvpReportService;

    public MvpReportController(MvpReportService mvpReportService) {
        this.mvpReportService = mvpReportService;
    }

    @PostMapping(value = "/report-run-mvp", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> reportMvp(
            @RequestHeader(value = "X-Machine-Key", required = false) String machineKey,
            @RequestHeader(value = "X-Plugin-Version", required = false) Integer pluginVersion,
            @RequestBody ReportRunMvpRequest request) {
        mvpReportService.submit(machineKey, pluginVersion, request);
        return ResponseEntity.noContent().build();
    }
}
