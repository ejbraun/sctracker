package com.howl.uwtracker.maps;

import com.howl.uwtracker.maps.dto.MapResponse;
import com.howl.uwtracker.repository.GameMapRepository;
import com.howl.uwtracker.repository.MapConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** specs/backend/00-overview.md's "Reference data" section. Protected by SessionAuthInterceptor (under /api/**). */
@RestController
public class MapController {

    private final GameMapRepository gameMapRepository;
    private final MapConfigRepository mapConfigRepository;

    public MapController(GameMapRepository gameMapRepository, MapConfigRepository mapConfigRepository) {
        this.gameMapRepository = gameMapRepository;
        this.mapConfigRepository = mapConfigRepository;
    }

    @GetMapping("/api/maps")
    public ResponseEntity<List<MapResponse>> list() {
        return ResponseEntity.ok(gameMapRepository.findAll().stream()
                .map(map -> MapResponse.from(map, mapConfigRepository.findByIdMapIdOrderByIdPartySizeAsc(map.getId())))
                .toList());
    }
}
