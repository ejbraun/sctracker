package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.MapConfig;
import com.howl.uwtracker.domain.MapConfigId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Supported {@code (map, party_size)} configurations, seeded by Liquibase migration
 * (specs/features/fow-and-party-size.md). Ingestion rejects any upload whose
 * {@code (map_id, party_members.length)} pair has no row here.
 */
public interface MapConfigRepository extends JpaRepository<MapConfig, MapConfigId> {

    /** All configs for one map, ascending by party size — powers {@code GET /api/maps}' config list. */
    List<MapConfig> findByIdMapIdOrderByIdPartySizeAsc(Integer mapId);
}
