package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "people")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // Public-facing display identity, separate from username (private, login-only). Nullable until
    // set via PATCH /api/account/alias.
    @Column(unique = true, length = 64)
    private String alias;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    // Gates POST /report-run-failure and POST /report-run-mvp (the latter has no permission check
    // of its own — it just reuses this flag). Granted/revoked by an admin via PATCH
    // /api/admin/users/{id}/can-report-failures (AdminUserController).
    @Column(name = "can_report_failures", nullable = false)
    private boolean canReportFailures;

    // Stamped by MachineKeyAuthenticationService on every machine-key request — the timestamp and
    // the X-Plugin-Version the plugin reported (the plugin actually talking to the backend).
    // Written via PersonRepository.recordPluginSeen (a bulk UPDATE by id), so there are no setters.
    // A non-null lastPluginSeenAt with a null lastSeenPluginVersion is a client too old to send the
    // header. lastSeenPluginVersion vs. the current manifest version drives both the 426 upload gate
    // (PluginVersionMetadataLoader) and the website's update banner (PluginVersionService.isOutdated).
    @Column(name = "last_plugin_seen_at")
    private Instant lastPluginSeenAt;

    @Column(name = "last_seen_plugin_version")
    private Integer lastSeenPluginVersion;

    protected Person() {
    }

    public Person(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isCanReportFailures() {
        return canReportFailures;
    }

    public void setCanReportFailures(boolean canReportFailures) {
        this.canReportFailures = canReportFailures;
    }

    public Instant getLastPluginSeenAt() {
        return lastPluginSeenAt;
    }

    public Integer getLastSeenPluginVersion() {
        return lastSeenPluginVersion;
    }
}
