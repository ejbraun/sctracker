package com.howl.uwtracker.modules;

import com.howl.uwtracker.admin.dto.AdminModuleResponse;
import com.howl.uwtracker.admin.dto.CreateModuleRequest;
import com.howl.uwtracker.admin.dto.UpdateModuleRequest;
import com.howl.uwtracker.domain.Module;
import com.howl.uwtracker.repository.ModuleRepository;
import com.howl.uwtracker.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CRUD for the {@code modules} registry, behind {@code /api/admin/modules}. {@code module_key} is
 * validated to a dot-free slug (safe in the {@code /modules/{key}/download} path) and is immutable
 * once set. The three seeded keys can't be deleted — disable them instead.
 */
@Service
public class ModuleAdminService {

    private static final Pattern MODULE_KEY = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
    private static final Set<String> PROTECTED_KEYS = Set.of("sctracker", "pp-exe", "pp-base");

    private final ModuleRepository moduleRepository;
    private final ModuleManifestCache moduleManifestCache;

    public ModuleAdminService(ModuleRepository moduleRepository, ModuleManifestCache moduleManifestCache) {
        this.moduleRepository = moduleRepository;
        this.moduleManifestCache = moduleManifestCache;
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
        return AdminModuleResponse.from(moduleRepository.save(module));
    }

    @Transactional
    public AdminModuleResponse update(String moduleKey, UpdateModuleRequest req) {
        Module module = moduleRepository.findByModuleKey(moduleKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "unknown module"));

        if (req.displayName() != null) {
            module.setDisplayName(requireNonBlank(req.displayName(), "display_name"));
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
}
