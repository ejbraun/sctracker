package com.howl.uwtracker.plugin;

import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.PluginDllVersion;
import com.howl.uwtracker.repository.PluginDllVersionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PluginVersionService {

    private final PluginDllVersionRepository pluginDllVersionRepository;

    public PluginVersionService(PluginDllVersionRepository pluginDllVersionRepository) {
        this.pluginDllVersionRepository = pluginDllVersionRepository;
    }

    /**
     * True if this person has never recorded a plugin download at all (nothing to compare a
     * timestamp against, so treat it as needing the current build), or if their last download
     * predates the currently-detected dll build. See PluginDllVersionInitializer for how/when that
     * detection happens.
     */
    public boolean isOutdated(Person person) {
        Instant lastDownload = person.getLastPluginDownloadAt();
        if (lastDownload == null) {
            return true;
        }
        return pluginDllVersionRepository.findById(PluginDllVersion.SINGLETON_ID)
                .map(version -> lastDownload.isBefore(version.getDetectedAt()))
                .orElse(false);
    }
}
