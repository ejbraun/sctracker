package com.howl.uwtracker.admin;

import com.howl.uwtracker.admin.dto.UnregisteredRunsCountResponse;
import com.howl.uwtracker.admin.dto.WipeUnregisteredRunsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only run cleanup — protected by {@link com.howl.uwtracker.auth.AdminAuthInterceptor}. */
@RestController
@RequestMapping("/api/admin/runs")
public class AdminRunController {

    private final AdminRunService adminRunService;

    public AdminRunController(AdminRunService adminRunService) {
        this.adminRunService = adminRunService;
    }

    /** Lets the admin UI show what a wipe would delete before the button is ever clicked. */
    @GetMapping("/unregistered-count")
    public ResponseEntity<UnregisteredRunsCountResponse> unregisteredCount() {
        return ResponseEntity.ok(new UnregisteredRunsCountResponse(adminRunService.countUnregisteredRuns()));
    }

    @PostMapping("/wipe-unregistered")
    public ResponseEntity<WipeUnregisteredRunsResponse> wipeUnregistered() {
        return ResponseEntity.ok(new WipeUnregisteredRunsResponse(adminRunService.wipeUnregisteredRuns()));
    }
}
