package com.howl.uwtracker.modules;

import com.howl.uwtracker.auth.CurrentPersonId;
import com.howl.uwtracker.domain.ModuleType;
import com.howl.uwtracker.modules.dto.ModuleEntitlementsResponse;
import com.howl.uwtracker.modules.dto.ModuleEntitlementsResponse.Entry;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Session-authenticated view of the logged-in person's module entitlements, for the account page —
 * the web counterpart to the machine-key {@code GET /module-entitlements}. Lives under {@code /api}
 * so {@code SessionAuthInterceptor} guarantees a logged-in user; entitlement is still a live DB read.
 *
 * <p>{@code GET /api/account/modules/{key}/download} exists because a browser {@code <a download>}
 * link can't send the {@code X-Machine-Key} header that {@code /modules/{key}/download} needs for a
 * gated module — so an entitled user downloads e.g. the {@code gwrl-install} launcher archive
 * through here instead. Each list entry's {@code download_url} already points at the right route.
 */
@RestController
@RequestMapping("/api/account/modules")
public class AccountModuleController {

    private final ModuleEntitlementService entitlementService;
    private final ModuleDownloadService downloadService;

    public AccountModuleController(ModuleEntitlementService entitlementService,
                                  ModuleDownloadService downloadService) {
        this.entitlementService = entitlementService;
        this.downloadService = downloadService;
    }

    @GetMapping
    public ResponseEntity<ModuleEntitlementsResponse> list(
            @CurrentPersonId Long personId,
            @RequestParam(value = "type", required = false) ModuleType type) {
        List<Entry> entries = entitlementService.forPerson(personId, type).modules().stream()
                .map(e -> new Entry(e.key(), e.displayName(), e.type(), e.isPublic(), e.version(), e.sha256(),
                        accountDownloadUrl(e.key())))
                .toList();
        return ResponseEntity.ok(new ModuleEntitlementsResponse(entries));
    }

    @GetMapping("/{key}/download")
    public ResponseEntity<InputStreamResource> download(@CurrentPersonId Long personId, @PathVariable String key) {
        return ModuleDownloadHttp.stream(downloadService.openForPerson(key, personId));
    }

    /** SCTracker keeps its dedicated public route; everything else the account page fetches through here. */
    private static String accountDownloadUrl(String moduleKey) {
        return ModuleKeys.SCTRACKER.equals(moduleKey)
                ? "/SCTracker.dll"
                : "/api/account/modules/" + moduleKey + "/download";
    }
}
