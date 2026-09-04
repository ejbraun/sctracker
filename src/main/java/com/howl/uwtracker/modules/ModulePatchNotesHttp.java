package com.howl.uwtracker.modules;

import com.howl.uwtracker.domain.Module;
import com.howl.uwtracker.modules.ModuleDownloadService.ModulePatchNotes;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * Shared response shaping for a resolved {@link ModulePatchNotes} — used by both the machine-key
 * {@code GET /modules/{key}/patch-notes} and the session
 * {@code GET /api/account/modules/{key}/patch-notes}, mirroring {@link ModuleDownloadHttp}. Always
 * {@code text/plain}; the filename is derived from the patch notes object's own basename (the object
 * path, not the artifact's), falling back to {@code <key>.patch.txt} if that's somehow empty.
 */
final class ModulePatchNotesHttp {

    private ModulePatchNotesHttp() {
    }

    static ResponseEntity<byte[]> stream(ModulePatchNotes notes) {
        Module module = notes.module();
        byte[] body = notes.text().getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("text/plain;charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename(module)).build());
        headers.setContentLength(body.length);
        headers.setCacheControl("no-cache");
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private static String filename(Module module) {
        String path = module.getPatchNotesObject();
        int lastSlash = path == null ? -1 : path.lastIndexOf('/');
        String basename = path != null && lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        return basename != null && !basename.isBlank() ? basename : module.getModuleKey() + ".patch.txt";
    }
}
