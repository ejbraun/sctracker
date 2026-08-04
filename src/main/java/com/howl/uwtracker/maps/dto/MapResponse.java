package com.howl.uwtracker.maps.dto;

import com.howl.uwtracker.domain.GameMap;

public record MapResponse(Integer id, String name) {

    public static MapResponse from(GameMap map) {
        return new MapResponse(map.getId(), map.getName());
    }
}
