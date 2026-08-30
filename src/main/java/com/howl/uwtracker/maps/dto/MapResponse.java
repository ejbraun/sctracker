package com.howl.uwtracker.maps.dto;

import com.howl.uwtracker.domain.GameMap;
import com.howl.uwtracker.domain.MapConfig;

import java.util.List;

/**
 * A supported map plus its {@code (party_size, role_model)} configurations — the frontend uses the
 * {@code partySize} values as the party-size selector options for that map. See
 * specs/features/fow-and-party-size.md.
 */
public record MapResponse(Integer id, String name, List<Config> configs) {

    public record Config(int partySize, String roleModel) {
    }

    public static MapResponse from(GameMap map, List<MapConfig> configs) {
        List<Config> configDtos = configs.stream()
                .map(c -> new Config(c.getPartySize(), c.getRoleModel() == null ? null : c.getRoleModel().wireValue()))
                .toList();
        return new MapResponse(map.getId(), map.getName(), configDtos);
    }
}
