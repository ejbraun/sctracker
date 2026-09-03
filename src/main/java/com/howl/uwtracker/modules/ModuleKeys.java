package com.howl.uwtracker.modules;

/** Well-known {@code modules.module_key} values the backend special-cases. */
public final class ModuleKeys {

    /**
     * The SCTracker plugin. Its registry row exists only so it shows up in {@code GET /artifacts};
     * its bytes and live version still come from {@code GET /SCTracker.dll} / {@link
     * com.howl.uwtracker.plugin.PluginArtifactCache}, not the generic module download path.
     */
    public static final String SCTRACKER = "sctracker";

    private ModuleKeys() {
    }
}
