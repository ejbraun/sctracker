package com.howl.uwtracker.modules;

import com.howl.uwtracker.domain.Module;
import com.howl.uwtracker.modules.ModuleDownloadService.ModuleDownload;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
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
 * (it's public) but nothing points callers here for it.
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
        ModuleDownload dl = moduleDownloadService.open(key, machineKey);
        Module module = dl.module();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(parseContentType(module.getContentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(module.getArtifactObject()).build());
        if (dl.artifact().size() >= 0) {
            headers.setContentLength(dl.artifact().size());
        }
        headers.setCacheControl("no-cache");
        if (module.getCurrentSha256() != null) {
            headers.setETag("\"" + module.getCurrentSha256() + "\"");
        }
        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(dl.artifact().stream()));
    }

    private static MediaType parseContentType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
