package com.howl.uwtracker.modules;

import com.howl.uwtracker.domain.Module;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.plugin.PluginStorageProperties;
import com.howl.uwtracker.repository.ModuleRepository;
import com.howl.uwtracker.repository.PersonModuleGrantRepository;
import com.howl.uwtracker.web.ApiException;
import com.howl.uwtracker.web.MachineKeyAuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

/**
 * Resolves a module-download request to an open {@link ReadableArtifact}, enforcing entitlement
 * first. Public modules (including the metadata-only {@code sctracker} row, though its real download
 * route is {@code /SCTracker.dll}) skip the entitlement check; every other module requires a grant —
 * for {@code GET /modules/{key}/download} the grant is checked against an {@code X-Machine-Key}'s
 * person ({@link #open}), for {@code GET /api/account/modules/{key}/download} against the logged-in
 * person ({@link #openForPerson}).
 *
 * <p>Bytes stream straight from the bucket per call — nothing is cached in memory — so a revoke
 * takes effect on the very next request. {@link #openPatchNotes}/{@link #openPatchNotesForPerson}
 * are the same entitlement check against a module's optional {@code patch_notes_object} instead of
 * its artifact — read whole (it's small plain text), not streamed.
 */
@Service
public class ModuleDownloadService {

    private static final Logger log = LoggerFactory.getLogger(ModuleDownloadService.class);

    private final ModuleRepository moduleRepository;
    private final PersonModuleGrantRepository grantRepository;
    private final MachineKeyAuthenticationService machineKeyAuth;
    private final ObjectProvider<ArtifactStorageClient> storageClient;
    private final PluginStorageProperties props;

    public ModuleDownloadService(ModuleRepository moduleRepository, PersonModuleGrantRepository grantRepository,
                                 MachineKeyAuthenticationService machineKeyAuth,
                                 ObjectProvider<ArtifactStorageClient> storageClient,
                                 PluginStorageProperties props) {
        this.moduleRepository = moduleRepository;
        this.grantRepository = grantRepository;
        this.machineKeyAuth = machineKeyAuth;
        this.storageClient = storageClient;
        this.props = props;
    }

    public record ModuleDownload(Module module, ReadableArtifact artifact) {
    }

    public record ModulePatchNotes(Module module, String text) {
    }

    /** Machine-key path: a non-public module needs an {@code X-Machine-Key} (401) whose person holds a grant (403). */
    public ModuleDownload open(String moduleKey, String rawMachineKey) {
        return open(moduleKey, machineKeyEntitled(rawMachineKey));
    }

    /** Session path: a non-public module needs the logged-in person to hold a grant (403 otherwise). */
    public ModuleDownload openForPerson(String moduleKey, Long personId) {
        return open(moduleKey, personEntitled(personId));
    }

    /**
     * Machine-key path for a module's optional patch notes — same entitlement rule as {@link #open},
     * plus a 404 when the module has no {@code patch_notes_object} configured at all.
     */
    public ModulePatchNotes openPatchNotes(String moduleKey, String rawMachineKey) {
        return openPatchNotes(moduleKey, machineKeyEntitled(rawMachineKey));
    }

    /** Session path for a module's optional patch notes — mirrors {@link #openForPerson}. */
    public ModulePatchNotes openPatchNotesForPerson(String moduleKey, Long personId) {
        return openPatchNotes(moduleKey, personEntitled(personId));
    }

    private Predicate<Module> machineKeyEntitled(String rawMachineKey) {
        return module -> {
            Person person = machineKeyAuth.authenticateWithoutVersionCheck(rawMachineKey); // 401
            return grantRepository.existsByIdPersonIdAndIdModuleId(person.getId(), module.getId());
        };
    }

    private Predicate<Module> personEntitled(Long personId) {
        return module -> grantRepository.existsByIdPersonIdAndIdModuleId(personId, module.getId());
    }

    private ModuleDownload open(String moduleKey, Predicate<Module> entitled) {
        Module module = resolveEntitledModule(moduleKey, entitled);

        ArtifactStorageClient client = storageClient.getIfAvailable();
        ReadableArtifact artifact = client == null ? null : client.openObject(module.artifactPath()).orElse(null);
        if (artifact == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "artifact unavailable");
        }
        if (artifact.size() > props.maxModuleDownloadBytes()) {
            close(artifact);
            log.warn("module {} artifact {} is {} bytes, over the {} limit — refusing to serve",
                    module.getModuleKey(), module.artifactPath(), artifact.size(), props.maxModuleDownloadBytes());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "artifact too large to serve");
        }
        return new ModuleDownload(module, artifact);
    }

    private ModulePatchNotes openPatchNotes(String moduleKey, Predicate<Module> entitled) {
        Module module = resolveEntitledModule(moduleKey, entitled);
        if (module.getPatchNotesObject() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "no patch notes for this module");
        }

        ArtifactStorageClient client = storageClient.getIfAvailable();
        byte[] bytes = client == null ? null : client.readObject(module.getPatchNotesObject()).orElse(null);
        if (bytes == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "patch notes unavailable");
        }
        return new ModulePatchNotes(module, new String(bytes, StandardCharsets.UTF_8));
    }

    /** Looks up an enabled module by key and enforces the entitlement predicate — shared by artifact and patch-notes reads. */
    private Module resolveEntitledModule(String moduleKey, Predicate<Module> entitled) {
        Module module = moduleRepository.findByModuleKey(moduleKey)
                .filter(Module::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "unknown module"));

        if (!module.isPublicAccess() && !entitled.test(module)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not entitled to this module");
        }
        return module;
    }

    private static void close(ReadableArtifact artifact) {
        try {
            artifact.stream().close();
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
