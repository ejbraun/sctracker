package com.howl.uwtracker.modules;

import com.howl.uwtracker.domain.Module;
import com.howl.uwtracker.modules.dto.ArtifactListResponse;
import com.howl.uwtracker.modules.dto.ArtifactSummaryResponse;
import com.howl.uwtracker.plugin.PluginVersionMetadata;
import com.howl.uwtracker.repository.ModuleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Backs the public {@code GET /artifacts} listing. Version/hash come from the live manifest where
 * available (via {@link ModuleManifestResolver}), falling back to the persisted {@code current_*}
 * columns (last known good, e.g. right after a restart before {@link ModuleManifestCache#prime()}
 * finishes).
 */
@Service
public class ModuleMetadataService {

    private final ModuleRepository moduleRepository;
    private final ModuleManifestResolver manifestResolver;

    public ModuleMetadataService(ModuleRepository moduleRepository, ModuleManifestResolver manifestResolver) {
        this.moduleRepository = moduleRepository;
        this.manifestResolver = manifestResolver;
    }

    public ArtifactListResponse list() {
        List<ArtifactSummaryResponse> artifacts = moduleRepository
                .findByEnabledTrueOrderBySortOrderAscModuleKeyAsc()
                .stream()
                .map(this::toSummary)
                .toList();
        return new ArtifactListResponse(artifacts);
    }

    /** App-relative download path for a module — SCTracker keeps its dedicated route. */
    static String downloadUrl(String moduleKey) {
        return ModuleKeys.SCTRACKER.equals(moduleKey) ? "/SCTracker.dll" : "/modules/" + moduleKey + "/download";
    }

    private ArtifactSummaryResponse toSummary(Module module) {
        PluginVersionMetadata manifest = manifestResolver.manifestFor(module);
        return new ArtifactSummaryResponse(
                module.getModuleKey(),
                module.getDisplayName(),
                module.isPublicAccess(),
                manifest != null ? manifest.version() : module.getCurrentVersion(),
                manifest != null ? manifest.compiledAt() : null,
                manifest != null ? manifest.sha256() : module.getCurrentSha256(),
                downloadUrl(module.getModuleKey()));
    }
}
