package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Singleton row (always id={@link #SINGLETON_ID}) tracking the SCTracker.dll build currently in the
 * plugin storage bucket and when it was first observed — see PluginDllVersionInitializer, which
 * upserts this whenever PluginArtifactCache pulls in a manifest with a new {@code sha256} (first
 * fetch or any later refresh). {@code content_hash} holds that manifest-declared {@code sha256}
 * (64-char lowercase hex); 023-create-plugin-dll-version.xml explains why a content hash rather than
 * a last-modified time.
 */
@Entity
@Table(name = "plugin_dll_version")
public class PluginDllVersion {

    public static final int SINGLETON_ID = 1;

    @Id
    @Column(columnDefinition = "TINYINT UNSIGNED")
    private Integer id;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    protected PluginDllVersion() {
    }

    public PluginDllVersion(String contentHash, Instant detectedAt) {
        this.id = SINGLETON_ID;
        this.contentHash = contentHash;
        this.detectedAt = detectedAt;
    }

    public Integer getId() {
        return id;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }
}
