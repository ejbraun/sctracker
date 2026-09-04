package com.howl.uwtracker.modules;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /modules/{key}/download} — streams a module artifact from the storage bucket after the
 * entitlement check in {@link ModuleDownloadService}. Top-level path; the explicit mapping outranks
 * {@code SpaFallbackController} (Spring dispatches to the most specific match), and {@code key} is a
 * dot-free slug so there's no collision with the static-asset handler either.
 *
 * <p>{@code sctracker} is served by {@code /SCTracker.dll} instead; this route still works for it
 * (it's public) but nothing points callers here for it. The logged-in web user downloads a gated
 * module through {@code GET /api/account/modules/{key}/download} instead — a browser link can't send
 * the {@code X-Machine-Key} header this route needs.
 */
@RestController
public class ModuleDownloadController {

    private final ModuleDownloadService moduleDownloadService;

    public ModuleDownloadController(ModuleDownloadService moduleDownloadService) {
        this.moduleDownloadService = moduleDownloadService;
    }

    @GetMapping("/modules/{key}/download")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable String key,
            @RequestHeader(value = "X-Machine-Key", required = false) String machineKey) {
        return ModuleDownloadHttp.stream(moduleDownloadService.open(key, machineKey));
    }
}
