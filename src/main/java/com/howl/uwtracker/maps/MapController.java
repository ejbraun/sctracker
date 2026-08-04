package com.howl.uwtracker.maps;

import com.howl.uwtracker.maps.dto.MapResponse;
import com.howl.uwtracker.repository.GameMapRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** specs/backend/00-overview.md's "Reference data" section. Protected by SessionAuthInterceptor (under /api/**). */
@RestController
public class MapController {

    private final GameMapRepository gameMapRepository;

    public MapController(GameMapRepository gameMapRepository) {
        this.gameMapRepository = gameMapRepository;
    }

    @GetMapping("/api/maps")
    public ResponseEntity<List<MapResponse>> list() {
        return ResponseEntity.ok(gameMapRepository.findAll().stream().map(MapResponse::from).toList());
    }
}
