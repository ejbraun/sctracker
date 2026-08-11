package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A GW1 item the plugin tracks drops for (kTrackedItems in SCTracker.cpp). {@code id} is the raw
 * model id (GW::Constants::ItemID) the plugin reports on {@code item_drops[].id} — the client never
 * sends a display name, so this table is the backend's own id -> name mapping. Reference data, same
 * shape/role as {@link Profession}.
 */
@Entity
@Table(name = "tracked_items")
public class TrackedItem {

    @Id
    @Column(columnDefinition = "INT UNSIGNED")
    private Integer id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    protected TrackedItem() {
    }

    public TrackedItem(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
