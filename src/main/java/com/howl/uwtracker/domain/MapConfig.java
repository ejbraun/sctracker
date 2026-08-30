package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One supported {@code (map, party_size)} configuration and the role model that applies to it — see
 * specs/features/fow-and-party-size.md. A map with no {@code MapConfig} rows is unsupported;
 * ingestion rejects the upload.
 */
@Entity
@Table(name = "map_configs")
public class MapConfig {

    @EmbeddedId
    private MapConfigId id;

    /** Nullable: a config with no role model leaves every participant's role null and is not role-gated. */
    @Convert(converter = RoleModelConverter.class)
    @Column(name = "role_model", length = 24)
    private RoleModel roleModel;

    protected MapConfig() {
    }

    public MapConfig(MapConfigId id, RoleModel roleModel) {
        this.id = id;
        this.roleModel = roleModel;
    }

    public MapConfigId getId() {
        return id;
    }

    public Integer getMapId() {
        return id.getMapId();
    }

    public Integer getPartySize() {
        return id.getPartySize();
    }

    public RoleModel getRoleModel() {
        return roleModel;
    }
}
