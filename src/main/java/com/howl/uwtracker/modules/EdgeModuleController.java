package com.howl.uwtracker.modules;

import com.howl.uwtracker.modules.dto.ModuleEntitlementsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /module-entitlements} — the launcher calls this on start/update with its
 * {@code X-Machine-Key} (the one the user pasted in from gwsctracker) and gets back the modules it's
 * allowed to see. Top-level path, key-only auth, no {@code X-Plugin-Version} gate — closest sibling
 * is {@code GET /can-report-run-failure}. See specs/backend/08-module-entitlements.md.
 */
@RestController
public class EdgeModuleController {

    private final ModuleEntitlementService moduleEntitlementService;

    public EdgeModuleController(ModuleEntitlementService moduleEntitlementService) {
        this.moduleEntitlementService = moduleEntitlementService;
    }

    @GetMapping("/module-entitlements")
    public ResponseEntity<ModuleEntitlementsResponse> entitlements(
            @RequestHeader(value = "X-Machine-Key", required = false) String machineKey) {
        return ResponseEntity.ok(moduleEntitlementService.forMachineKey(machineKey));
    }
}
