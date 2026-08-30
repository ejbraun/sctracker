package com.howl.uwtracker.plugin;

import com.howl.uwtracker.web.ApiException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the SCTracker plugin binary at the top-level URL {@code GET /SCTracker.dll} — the URL the
 * frontend Account page links for download. Previously this was a static file inside the jar; it's
 * now the bytes {@link PluginArtifactCache} holds from the storage bucket.
 *
 * <p>An explicit {@code @GetMapping} outranks {@code SpaFallbackController}'s {@code {path:[^.]*}}
 * mappings (which never match a path containing a dot anyway) and the default static-resource
 * handler, so routing is unambiguous. 503 while the cache has never populated (no bucket, or the
 * bucket was unreachable at every attempt so far).
 */
@RestController
public class PluginDllController {

    private final PluginArtifactCache cache;

    public PluginDllController(PluginArtifactCache cache) {
        this.cache = cache;
    }

    @GetMapping("/SCTracker.dll")
    public ResponseEntity<byte[]> downloadDll() {
        byte[] bytes = cache.getDll();
        if (bytes == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "plugin binary unavailable");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename("SCTracker.dll").build());
        headers.setContentLength(bytes.length);
        headers.setCacheControl("no-cache");
        PluginVersionMetadata manifest = cache.getManifest();
        if (manifest != null && manifest.sha256() != null) {
            headers.setETag("\"" + manifest.sha256() + "\"");
        }
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
