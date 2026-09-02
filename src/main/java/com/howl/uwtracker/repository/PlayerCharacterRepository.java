package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.PlayerCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerCharacterRepository extends JpaRepository<PlayerCharacter, Long> {

    List<PlayerCharacter> findByPerson_Id(Long personId);

    List<PlayerCharacter> findByPerson_IdOrderByCharacterNameAsc(Long personId);

    Optional<PlayerCharacter> findByCharacterName(String characterName);

    boolean existsByCharacterName(String characterName);

    /** Backs the Run History "character" filter dropdown — every character system-wide, not just the caller's. */
    List<PlayerCharacter> findAllByOrderByCharacterNameAsc();
}
