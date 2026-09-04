package com.howl.uwtracker.modules;

import com.howl.uwtracker.domain.Module;
import com.howl.uwtracker.modules.ModuleDownloadService.ModuleDownload;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Shared response shaping for a resolved {@link ModuleDownload} — used by both the machine-key
 * {@code GET /modules/{key}/download} and the session {@code GET /api/account/modules/{key}/download}
 * so the two routes stream identical bytes and headers ({@code Content-Disposition} filename from the
 * artifact object, {@code ETag} from the manifest sha, {@code Cache-Control: no-cache}).
 */
final class ModuleDownloadHttp {

    private ModuleDownloadHttp() {
    }

    static ResponseEntity<InputStreamResource> stream(ModuleDownload dl) {
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
