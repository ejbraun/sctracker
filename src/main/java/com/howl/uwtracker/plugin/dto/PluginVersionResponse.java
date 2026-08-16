package com.howl.uwtracker.plugin.dto;

import com.howl.uwtracker.plugin.PluginVersionMetadata;

import java.time.Instant;

public record PluginVersionResponse(int version, Instant compiledAt) {

    public static PluginVersionResponse from(PluginVersionMetadata metadata) {
        return new PluginVersionResponse(metadata.version(), metadata.compiledAt());
    }
}
