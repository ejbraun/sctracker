package com.howl.uwtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One downloadable artifact gwsctracker hosts for the ProjectPotato launcher — see
 * specs/backend/08-module-entitlements.md and changeset 043-create-modules.xml.
 *
 * <p>{@code publicAccess} (column {@code is_public}) means anyone can download it; otherwise
 * {@code GET /modules/{key}/download} requires an {@code X-Machine-Key} whose person holds a
 * {@link PersonModuleGrant} for this module. The {@code current*} fields are a cheap cache of the
 * artifact's manifest, refreshed by ModuleManifestCache — SCTracker's own singleton
 * {@link PluginDllVersion} table is left untouched.
 *
 * <p>The field is {@code publicAccess}, not {@code isPublic}: Hibernate would derive the property
 * name {@code public} (a Java keyword) from an {@code is}-prefixed boolean field and fail. The
 * column stays {@code is_public}; the getter is {@link #isPublicAccess()}.
 */
@Entity
@Table(name = "modules")
public class Module {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "module_key", nullable = false, unique = true, length = 64)
    private String moduleKey;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "is_public", nullable = false)
    private boolean publicAccess;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "bucket_prefix", nullable = false, length = 255)
    private String bucketPrefix;

    @Column(name = "artifact_object", nullable = false, length = 255)
    private String artifactObject;

    /** Full object path from the bucket root, or {@code null} when the artifact has no manifest sidecar. */
    @Column(name = "manifest_object", length = 255)
    private String manifestObject;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType = "application/octet-stream";

    // Cached from the manifest by ModuleManifestCache; null until the artifact is first seen.
    @Column(name = "current_version")
    private Integer currentVersion;

    @Column(name = "current_sha256", length = 64)
    private String currentSha256;

    @Column(name = "version_detected_at")
    private Instant versionDetectedAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected Module() {
    }

    public Module(String moduleKey, String displayName, boolean publicAccess, String bucketPrefix,
                  String artifactObject, String manifestObject, String contentType, int sortOrder) {
        this.moduleKey = moduleKey;
        this.displayName = displayName;
        this.publicAccess = publicAccess;
        this.bucketPrefix = bucketPrefix;
        this.artifactObject = artifactObject;
        this.manifestObject = manifestObject;
        if (contentType != null && !contentType.isBlank()) {
            this.contentType = contentType;
        }
        this.sortOrder = sortOrder;
    }

    /** Full path of the artifact object within the storage bucket. */
    public String artifactPath() {
        return bucketPrefix + "/" + artifactObject;
    }

    public Long getId() {
        return id;
    }

    public String getModuleKey() {
        return moduleKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isPublicAccess() {
        return publicAccess;
    }

    public void setPublicAccess(boolean publicAccess) {
        this.publicAccess = publicAccess;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBucketPrefix() {
        return bucketPrefix;
    }

    public void setBucketPrefix(String bucketPrefix) {
        this.bucketPrefix = bucketPrefix;
    }

    public String getArtifactObject() {
        return artifactObject;
    }

    public void setArtifactObject(String artifactObject) {
        this.artifactObject = artifactObject;
    }

    public String getManifestObject() {
        return manifestObject;
    }

    public void setManifestObject(String manifestObject) {
        this.manifestObject = manifestObject;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Integer currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getCurrentSha256() {
        return currentSha256;
    }

    public void setCurrentSha256(String currentSha256) {
        this.currentSha256 = currentSha256;
    }

    public Instant getVersionDetectedAt() {
        return versionDetectedAt;
    }

    public void setVersionDetectedAt(Instant versionDetectedAt) {
        this.versionDetectedAt = versionDetectedAt;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
