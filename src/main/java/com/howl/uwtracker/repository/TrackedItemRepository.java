package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.TrackedItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackedItemRepository extends JpaRepository<TrackedItem, Integer> {
}
