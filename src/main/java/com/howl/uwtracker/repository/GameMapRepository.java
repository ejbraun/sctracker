package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.GameMap;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * maps is a curated, well-defined set seeded by Liquibase migration (specs/backend/01), not
 * auto-discovered from whatever map_id an upload happens to carry — {@code UploadRunService} rejects
 * uploads for any map_id not already present here.
 */
public interface GameMapRepository extends JpaRepository<GameMap, Integer> {
}
