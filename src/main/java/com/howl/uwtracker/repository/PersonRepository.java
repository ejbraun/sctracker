package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long> {

    Optional<Person> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByAlias(String alias);

    /** Backs the Run History "person" filter dropdown — only people who've bothered to set one are choosable. */
    List<Person> findByAliasIsNotNullOrderByAliasAsc();
}
