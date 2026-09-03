package com.howl.uwtracker.modules;

import com.howl.uwtracker.domain.Module;
import com.howl.uwtracker.plugin.PluginVersionMetadata;
import com.howl.uwtracker.plugin.PluginVersionMetadataLoader;
import org.springframework.stereotype.Component;

/**
 * One place for the "where does this module's live manifest come from" decision: {@code sctracker}
 * reuses the SCTracker plugin's own cache; every other module goes through {@link ModuleManifestCache}.
 * Shared by {@link ModuleMetadataService} and {@link ModuleEntitlementService} so the special case
 * can't drift between them.
 */
@Component
public class ModuleManifestResolver {

    private final ModuleManifestCache moduleManifestCache;
    private final PluginVersionMetadataLoader pluginVersionMetadataLoader;

    public ModuleManifestResolver(ModuleManifestCache moduleManifestCache,
                                  PluginVersionMetadataLoader pluginVersionMetadataLoader) {
        this.moduleManifestCache = moduleManifestCache;
        this.pluginVersionMetadataLoader = pluginVersionMetadataLoader;
    }

    /** Live manifest for the module, or {@code null} if none has loaded (caller falls back to {@code current_*}). */
    public PluginVersionMetadata manifestFor(Module module) {
        if (ModuleKeys.SCTRACKER.equals(module.getModuleKey())) {
            return pluginVersionMetadataLoader.getCurrent();
        }
        return moduleManifestCache.getManifest(module).orElse(null);
    }
}
