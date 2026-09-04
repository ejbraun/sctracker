package com.howl.uwtracker.plugin;

import com.howl.uwtracker.domain.Person;
import org.springframework.stereotype.Component;

@Component
public class PluginVersionService {

    private final PluginVersionMetadataLoader pluginVersionMetadataLoader;

    public PluginVersionService(PluginVersionMetadataLoader pluginVersionMetadataLoader) {
        this.pluginVersionMetadataLoader = pluginVersionMetadataLoader;
    }

    /**
     * Whether to show this person the website's "new plugin version available" banner. Driven by the
     * build their plugin last advertised over {@code X-Plugin-Version}
     * ({@code people.last_seen_plugin_version}, stamped by
     * {@link com.howl.uwtracker.web.MachineKeyAuthenticationService} on every machine-key request),
     * compared against the current manifest version — the very same comparison
     * {@link PluginVersionMetadataLoader#requireCurrentVersion} (the 426 upload gate) and the
     * "Players On An Outdated Plugin" loserboard use, so the banner and the gate can never disagree.
     *
     * <p>Fails open when the manifest hasn't loaded (no bucket configured, or unreachable): with no
     * known current version, nobody is classified as outdated — same posture as
     * {@code requireCurrentVersion} and {@code LoserboardService.outdatedPlugins}.
     *
     * <p>A person whose plugin has never authenticated ({@code last_plugin_seen_at} null) still gets
     * the banner — nothing has reported a version, so treat it as "go install the current build". A
     * sighting with a null version (a client too old to send the header) counts as outdated too.
     */
    public boolean isOutdated(Person person) {
        PluginVersionMetadata current = pluginVersionMetadataLoader.getCurrent();
        if (current == null) {
            return false;
        }
        if (person.getLastPluginSeenAt() == null) {
            return true;
        }
        Integer lastSeen = person.getLastSeenPluginVersion();
        return lastSeen == null || lastSeen < current.version();
    }
}
