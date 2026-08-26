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

    // Set by POST /api/plugin/download. NULL means "never downloaded" — the "new plugin version
    // available" banner (see PluginDllVersion) shows for this case too, same as a stale non-null
    // timestamp, since there's nothing here yet to compare against the current build.
    @Column(name = "last_plugin_download_at")
    private Instant lastPluginDownloadAt;

    // Gates POST /report-run-failure and POST /report-run-mvp (the latter has no permission check
    // of its own — it just reuses this flag). Granted/revoked by an admin via PATCH
    // /api/admin/users/{id}/can-report-failures (AdminUserController).
    @Column(name = "can_report_failures", nullable = false)
    private boolean canReportFailures;

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

    public Instant getLastPluginDownloadAt() {
        return lastPluginDownloadAt;
    }

    public void setLastPluginDownloadAt(Instant lastPluginDownloadAt) {
        this.lastPluginDownloadAt = lastPluginDownloadAt;
    }

    public boolean isCanReportFailures() {
        return canReportFailures;
    }

    public void setCanReportFailures(boolean canReportFailures) {
        this.canReportFailures = canReportFailures;
    }
}
