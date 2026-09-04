package com.howl.uwtracker.modules;

import com.howl.uwtracker.domain.Module;
import com.howl.uwtracker.repository.ModuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a {@link ModuleVersionChangedEvent} back to the module's {@code current_version} /
 * {@code current_sha256} / {@code version_detected_at} columns.
 *
 * <p>This mutates the managed entity inside a {@code @Transactional} boundary and lets the flush
 * persist it; the event is published from {@link ModuleManifestCache} (a different bean) so this
 * listener is invoked through the proxy.
 */
@Component
public class ModuleVersionInitializer {

    private static final Logger log = LoggerFactory.getLogger(ModuleVersionInitializer.class);

    private final ModuleRepository moduleRepository;

    public ModuleVersionInitializer(ModuleRepository moduleRepository) {
        this.moduleRepository = moduleRepository;
    }

    @EventListener
    @Transactional
    public void onModuleVersionChanged(ModuleVersionChangedEvent event) {
        Module module = moduleRepository.findById(event.moduleId()).orElse(null);
        if (module == null) {
            return; // deleted between the fetch and here — nothing to record
        }
        module.setCurrentVersion(event.version());
        module.setCurrentSha256(event.sha256());
        module.setVersionDetectedAt(event.detectedAt());
        log.info("module {} now at version {} (sha256={})", module.getModuleKey(), event.version(), event.sha256());
    }
}
