package com.howl.uwtracker.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.howl.uwtracker.admin.dto.AdminModuleResponse;
import com.howl.uwtracker.admin.dto.BucketScanResponse;
import com.howl.uwtracker.admin.dto.CreateModuleRequest;
import com.howl.uwtracker.admin.dto.DiscoveredModuleResponse;
import com.howl.uwtracker.admin.dto.ModuleUpdateResponse;
import com.howl.uwtracker.admin.dto.UpdateModuleRequest;
import com.howl.uwtracker.domain.Module;
import com.howl.uwtracker.domain.ModuleType;
import com.howl.uwtracker.plugin.PluginVersionMetadata;
import com.howl.uwtracker.repository.ModuleRepository;
import com.howl.uwtracker.web.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CRUD for the {@code modules} registry, behind {@code /api/admin/modules}, plus {@link #discover()}
 * which scans the bucket both for unregistered artifact folders and for fields an existing module
 * hasn't picked up yet (see {@link #findUpdates}). {@code module_key} is validated to a dot-free
 * slug (safe in the {@code /modules/{key}/download} path) and is immutable once set. The seeded
 * {@code sctracker} key can't be deleted — disable it instead.
 */
@Service
public class ModuleAdminService {

    private static final Pattern MODULE_KEY = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
    // Only SCTracker is special: its bytes come from PluginArtifactCache / GET /SCTracker.dll, and
    // ModuleKeys.SCTRACKER / ModuleManifestResolver / ModuleMetadataService key off it.
    private static final Set<String> PROTECTED_KEYS = Set.of("sctracker");

    /** Bucket prefixes the scan walks (each ends with {@code /}), and the module type to suggest for
     *  finds under it — {@code plugins/} holds GWToolbox plugin DLLs, {@code launcher/} holds the
     *  GW Launcher Reforged (GWRL) launcher's own components. */
    private record ScanPrefix(String prefix, ModuleType suggestedType) {
    }

    private static final List<ScanPrefix> SCAN_PREFIXES = List.of(
            new ScanPrefix("plugins/", ModuleType.PLUGIN),
            new ScanPrefix("launcher/", ModuleType.MODULE));

    /** Artifact filename extensions probed inside a discovered folder, in priority order — a plugin
     *  is {@code <Folder>.dll}, the launcher install archive {@code <Folder>.zip}, the base exe
     *  {@code <Folder>.exe}. */
    private static final List<String> ARTIFACT_EXTENSIONS = List.of(".dll", ".zip", ".exe");

    private final ModuleRepository moduleRepository;
    private final ModuleManifestCache moduleManifestCache;
    private final ObjectProvider<ArtifactStorageClient> storageClient;
    private final ObjectMapper objectMapper;

    public ModuleAdminService(ModuleRepository moduleRepository, ModuleManifestCache moduleManifestCache,
                              ObjectProvider<ArtifactStorageClient> storageClient, ObjectMapper objectMapper) {
        this.moduleRepository = moduleRepository;
        this.moduleManifestCache = moduleManifestCache;
        this.storageClient = storageClient;
        this.objectMapper = objectMapper;
    }

    /**
     * One bucket walk, two results: {@code discovered} is {@code plugins/<Folder>/} and
     * {@code launcher/<Folder>/} directories with a recognisable artifact
     * ({@code <Folder>.dll} / {@code .zip} / {@code .exe}) but no {@code modules} row at all yet —
     * the admin imports one via {@link #create}. {@code updates} is the opposite direction:
     * already-registered modules whose bucket folder now has a {@code .version.json} or
     * {@code .patch.txt} the row doesn't reference yet (e.g. patch notes added well after the
     * module was first registered) — see {@link #findUpdates}. Both empty when no bucket is
     * configured.
     */
    @Transactional(readOnly = true)
    public BucketScanResponse discover() {
        ArtifactStorageClient client = storageClient.getIfAvailable();
        if (client == null) {
            return new BucketScanResponse(List.of(), List.of());
        }
        List<Module> allModules = moduleRepository.findAll();
        Set<String> registeredPrefixes = allModules.stream().map(Module::getBucketPrefix).collect(Collectors.toSet());

        List<DiscoveredModuleResponse> discovered = new ArrayList<>();
        for (ScanPrefix scan : SCAN_PREFIXES) {
            for (String folder : client.listSubdirectories(scan.prefix())) {
                String bucketPrefix = scan.prefix() + folder;
                if (registeredPrefixes.contains(bucketPrefix)) {
                    continue;
                }
                String artifactObject = findArtifactObject(client, bucketPrefix, folder);
                if (artifactObject == null) {
                    continue; // a stray directory, no recognisable artifact
                }
                String manifestObject = bucketPrefix + "/" + folder + ".version.json";
                boolean hasManifest = client.objectExists(manifestObject);
                String patchNotesObject = bucketPrefix + "/" + folder + ".patch.txt";
                boolean hasPatchNotes = client.objectExists(patchNotesObject);
                discovered.add(new DiscoveredModuleResponse(
                        folder,
                        slugify(folder),
                        hasManifest ? manifestName(client, manifestObject, folder) : folder,
                        scan.suggestedType(),
                        bucketPrefix,
                        artifactObject,
                        hasManifest ? manifestObject : null,
                        hasManifest,
                        hasPatchNotes ? patchNotesObject : null,
                        hasPatchNotes));
            }
        }
        discovered.sort(Comparator.comparing(DiscoveredModuleResponse::folderName, String.CASE_INSENSITIVE_ORDER));

        List<ModuleUpdateResponse> updates = findUpdates(client, allModules);
        return new BucketScanResponse(discovered, updates);
    }

    /**
     * For every already-registered module, re-derives the {@code .version.json} / {@code .patch.txt}
     * paths discovery would compute for its {@code bucketPrefix} and proposes filling in whichever
     * of the two the row doesn't already have — never proposes changing or clearing a field that's
     * already set, and never touches {@code artifact_object} at all. Only modules with at least one
     * proposal are returned.
     */
    private List<ModuleUpdateResponse> findUpdates(ArtifactStorageClient client, List<Module> allModules) {
        List<ModuleUpdateResponse> updates = new ArrayList<>();
        for (Module module : allModules) {
            String folder = folderName(module.getBucketPrefix());
            String proposedManifest = null;
            if (module.getManifestObject() == null) {
                String candidate = module.getBucketPrefix() + "/" + folder + ".version.json";
                proposedManifest = client.objectExists(candidate) ? candidate : null;
            }
            String proposedPatchNotes = null;
            if (module.getPatchNotesObject() == null) {
                String candidate = module.getBucketPrefix() + "/" + folder + ".patch.txt";
                proposedPatchNotes = client.objectExists(candidate) ? candidate : null;
            }
            if (proposedManifest != null || proposedPatchNotes != null) {
                updates.add(new ModuleUpdateResponse(
                        module.getModuleKey(), module.getDisplayName(), module.getBucketPrefix(),
                        proposedManifest, proposedPatchNotes));
            }
        }
        updates.sort(Comparator.comparing(ModuleUpdateResponse::moduleKey, String.CASE_INSENSITIVE_ORDER));
        return updates;
    }

    /** {@code "plugins/PP-Vanquish"} -> {@code "PP-Vanquish"} — the folder name discovery keys sidecar filenames off. */
    private static String folderName(String bucketPrefix) {
        int lastSlash = bucketPrefix.lastIndexOf('/');
        return lastSlash < 0 ? bucketPrefix : bucketPrefix.substring(lastSlash + 1);
    }

    /** First of {@code <folder>.dll} / {@code .zip} / {@code .exe} that exists under {@code bucketPrefix}, or null. */
    private static String findArtifactObject(ArtifactStorageClient client, String bucketPrefix, String folder) {
        for (String ext : ARTIFACT_EXTENSIONS) {
            String candidate = folder + ext;
            if (client.objectExists(bucketPrefix + "/" + candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<AdminModuleResponse> list() {
        return moduleRepository.findAllByOrderBySortOrderAscModuleKeyAsc().stream()
                .map(AdminModuleResponse::from)
                .toList();
    }

    @Transactional
    public AdminModuleResponse create(CreateModuleRequest req) {
        String key = req.moduleKey() == null ? "" : req.moduleKey().trim();
        if (!MODULE_KEY.matcher(key).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "module_key must match ^[a-z0-9][a-z0-9-]{0,63}$");
        }
        if (moduleRepository.existsByModuleKey(key)) {
            throw new ApiException(HttpStatus.CONFLICT, "a module with that key already exists");
        }
        Module module = new Module(
                key,
                requireNonBlank(req.displayName(), "display_name"),
                Boolean.TRUE.equals(req.isPublic()),
                requireNonBlank(req.bucketPrefix(), "bucket_prefix"),
                requireNonBlank(req.artifactObject(), "artifact_object"),
                blankToNull(req.manifestObject()),
                req.contentType(),
                req.sortOrder() == null ? 0 : req.sortOrder());
        if (req.type() != null) {
            module.setType(req.type());
        }
        module.setPatchNotesObject(blankToNull(req.patchNotesObject()));
        return AdminModuleResponse.from(moduleRepository.save(module));
    }

    @Transactional
    public AdminModuleResponse update(String moduleKey, UpdateModuleRequest req) {
        Module module = moduleRepository.findByModuleKey(moduleKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "unknown module"));

        if (req.displayName() != null) {
            module.setDisplayName(requireNonBlank(req.displayName(), "display_name"));
        }
        if (req.type() != null) {
            module.setType(req.type());
        }
        if (req.isPublic() != null) {
            module.setPublicAccess(req.isPublic());
        }
        if (req.enabled() != null) {
            module.setEnabled(req.enabled());
        }
        boolean pathsChanged = false;
        if (req.bucketPrefix() != null) {
            module.setBucketPrefix(requireNonBlank(req.bucketPrefix(), "bucket_prefix"));
            pathsChanged = true;
        }
        if (req.artifactObject() != null) {
            module.setArtifactObject(requireNonBlank(req.artifactObject(), "artifact_object"));
            pathsChanged = true;
        }
        if (req.manifestObject() != null) {
            module.setManifestObject(blankToNull(req.manifestObject()));
            pathsChanged = true;
        }
        if (req.patchNotesObject() != null) {
            // Not part of pathsChanged: patch notes aren't manifest-cached (ModuleManifestCache only
            // ever reads manifestObject), so there's nothing to evict here — the next download just
            // reads the new path.
            module.setPatchNotesObject(blankToNull(req.patchNotesObject()));
        }
        if (req.contentType() != null && !req.contentType().isBlank()) {
            module.setContentType(req.contentType().trim());
        }
        if (req.sortOrder() != null) {
            module.setSortOrder(req.sortOrder());
        }
        if (pathsChanged) {
            moduleManifestCache.evict(module.getId()); // object paths moved — re-fetch the manifest
        }
        return AdminModuleResponse.from(module);
    }

    @Transactional
    public void delete(String moduleKey) {
        Module module = moduleRepository.findByModuleKey(moduleKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "unknown module"));
        if (PROTECTED_KEYS.contains(moduleKey)) {
            throw new ApiException(HttpStatus.CONFLICT, "this module is built-in; disable it instead of deleting");
        }
        moduleManifestCache.evict(module.getId());
        moduleRepository.delete(module); // person_module_grants rows cascade
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " required");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** {@code "PP-Vanquish"} -> {@code "pp-vanquish"}; a best-effort suggestion the admin can override. */
    private static String slugify(String folder) {
        String s = folder.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.length() > 64) {
            s = s.substring(0, 64).replaceAll("-+$", "");
        }
        return s.isEmpty() ? "module" : s;
    }

    private String manifestName(ArtifactStorageClient client, String manifestObject, String fallback) {
        return client.readObject(manifestObject).map(bytes -> {
            try {
                PluginVersionMetadata m = objectMapper.readValue(bytes, PluginVersionMetadata.class);
                return m.name() != null && !m.name().isBlank() ? m.name() : fallback;
            } catch (Exception e) {
                return fallback;
            }
        }).orElse(fallback);
    }
}
