package com.howl.uwtracker.ingestion;

import com.howl.uwtracker.ingestion.dto.UploadRunRequest;
import com.howl.uwtracker.ingestion.dto.UploadRunResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** specs/backend/02-ingestion-upload-run.md — the only endpoint the GW1 SDK plugin calls. */
@RestController
public class UploadRunController {

    private final UploadRunService uploadRunService;

    public UploadRunController(UploadRunService uploadRunService) {
        this.uploadRunService = uploadRunService;
    }

    @PostMapping(value = "/upload-run", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UploadRunResponse> uploadRun(
            @RequestHeader(value = "X-Machine-Key", required = false) String machineKey,
            @RequestBody UploadRunRequest request) {
        return ResponseEntity.ok(uploadRunService.processUpload(machineKey, request));
    }
}
