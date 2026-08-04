package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.RoleObjective;
import com.howl.uwtracker.domain.RoleObjectiveId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleObjectiveRepository extends JpaRepository<RoleObjective, RoleObjectiveId> {

    boolean existsById_MapIdAndId_ObjectiveNameAndId_Role(Integer mapId, String objectiveName, String role);

    /** Which roles are gated in for this map/objective — backs the Sections "user(s)" column. */
    List<RoleObjective> findById_MapIdAndId_ObjectiveName(Integer mapId, String objectiveName);
}
