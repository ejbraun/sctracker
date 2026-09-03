package com.howl.uwtracker.modules;

import com.howl.uwtracker.domain.ModuleType;
import com.howl.uwtracker.modules.dto.ArtifactListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (no session, no machine key) — just "what artifacts exist and at what version", the same
 * non-sensitive posture as {@code GET /plugin-version}. Top-level, not under {@code /api/**}, per
 * the plugin-facing endpoint convention. The gated bytes still require a key at
 * {@code GET /modules/{key}/download}. See specs/backend/08-module-entitlements.md.
 */
@RestController
public class ModuleMetadataController {

    private final ModuleMetadataService moduleMetadataService;

    public ModuleMetadataController(ModuleMetadataService moduleMetadataService) {
        this.moduleMetadataService = moduleMetadataService;
    }

    @GetMapping("/artifacts")
    public ResponseEntity<ArtifactListResponse> list(
            @RequestParam(value = "type", required = false) ModuleType type) {
        return ResponseEntity.ok(moduleMetadataService.list(type));
    }
}
