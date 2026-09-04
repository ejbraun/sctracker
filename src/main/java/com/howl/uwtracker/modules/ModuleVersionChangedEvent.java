package com.howl.uwtracker.modules;

import java.time.Instant;

/**
 * Published by {@link ModuleManifestCache} when a module's manifest {@code sha256} changes (first
 * fetch or a later build). {@link ModuleVersionInitializer} writes it back to the module's
 * {@code current_*} columns.
 */
public record ModuleVersionChangedEvent(Long moduleId, int version, String sha256, Instant detectedAt) {
}
