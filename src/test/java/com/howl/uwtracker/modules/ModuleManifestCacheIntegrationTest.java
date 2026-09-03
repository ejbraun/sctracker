package com.howl.uwtracker.modules;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.domain.Module;
import com.howl.uwtracker.plugin.FakePluginStorageConfig;
import com.howl.uwtracker.repository.ModuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PR 2 adds no HTTP surface — this proves {@link ModuleManifestCache} pulls a module's manifest
 * from the (fake) store and {@link ModuleVersionInitializer} writes the {@code current_*} columns.
 */
class ModuleManifestCacheIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ModuleManifestCache cache;

    @Autowired
    ModuleRepository moduleRepository;

    @Test
    void populatesCurrentColumnsFromManifest() {
        long id = seedModule("pp-vanquish", false);
        Module module = moduleRepository.findById(id).orElseThrow();

        assertThat(cache.getManifest(module)).get().satisfies(m -> {
            assertThat(m.version()).isEqualTo(FakePluginStorageConfig.FAKE_VERSION);
            assertThat(m.sha256()).isNotBlank();
        });

        Module reloaded = moduleRepository.findById(id).orElseThrow();
        assertThat(reloaded.getCurrentVersion()).isEqualTo(FakePluginStorageConfig.FAKE_VERSION);
        assertThat(reloaded.getCurrentSha256()).isNotBlank();
        assertThat(reloaded.getVersionDetectedAt()).isNotNull();
    }

    @Test
    void moduleWithNoManifestObjectYieldsEmptyAndNoColumns() {
        long id = seedModule("pp-nomanifest", true);
        jdbcTemplate.update("UPDATE modules SET manifest_object = NULL WHERE id = ?", id);
        Module module = moduleRepository.findById(id).orElseThrow();

        assertThat(cache.getManifest(module)).isEmpty();
        assertThat(moduleRepository.findById(id).orElseThrow().getCurrentVersion()).isNull();
    }

    @Test
    void reFetchWithUnchangedShaStaysPopulated() {
        long id = seedModule("pp-stable", false);
        Module module = moduleRepository.findById(id).orElseThrow();

        assertThat(cache.getManifest(module)).isPresent();
        cache.evict(id);
        assertThat(cache.getManifest(module)).isPresent();

        assertThat(moduleRepository.findById(id).orElseThrow().getCurrentVersion())
                .isEqualTo(FakePluginStorageConfig.FAKE_VERSION);
    }
}
