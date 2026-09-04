package com.howl.uwtracker.web;

import com.howl.uwtracker.domain.MachineKey;
import com.howl.uwtracker.domain.OutdatedUploadAttempt;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.plugin.PluginVersionMetadataLoader;
import com.howl.uwtracker.repository.MachineKeyRepository;
import com.howl.uwtracker.repository.OutdatedUploadAttemptRepository;
import com.howl.uwtracker.repository.PersonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Shared X-Machine-Key authentication for every machine-key-authenticated endpoint (currently
 * /upload-run, /report-run-failure, /can-report-run-failure, /report-run-mvp) — kept in com.howl.uwtracker.web
 * rather than any one feature package, same reasoning as {@link MachineKeyHasher}.
 */
@Service
public class MachineKeyAuthenticationService {

    private final MachineKeyRepository machineKeyRepository;
    private final PersonRepository personRepository;
    private final PluginVersionMetadataLoader pluginVersionMetadataLoader;
    private final OutdatedUploadAttemptRepository outdatedUploadAttemptRepository;

    public MachineKeyAuthenticationService(MachineKeyRepository machineKeyRepository, PersonRepository personRepository,
                                            PluginVersionMetadataLoader pluginVersionMetadataLoader,
                                            OutdatedUploadAttemptRepository outdatedUploadAttemptRepository) {
        this.machineKeyRepository = machineKeyRepository;
        this.personRepository = personRepository;
        this.pluginVersionMetadataLoader = pluginVersionMetadataLoader;
        this.outdatedUploadAttemptRepository = outdatedUploadAttemptRepository;
    }

    /**
     * @param pluginVersion the calling client's own declared build ({@code X-Plugin-Version}
     *                       header), or null if it didn't send one (very old builds predating this
     *                       header entirely). Checked after key auth (so an invalid key still yields
     *                       401, not a version-requirement leak to an unauthenticated caller) — see
     *                       {@link PluginVersionMetadataLoader#requireCurrentVersion}.
     * @return the fully-loaded {@link Person} the given raw machine key belongs to.
     */
    public Person authenticate(String rawMachineKey, Integer pluginVersion) {
        MachineKey machineKey = lookupMachineKey(rawMachineKey);
        recordPluginSeen(machineKey, pluginVersion);
        pluginVersionMetadataLoader.requireCurrentVersion(pluginVersion);
        return loadPerson(machineKey);
    }

    /**
     * Key-only authentication for callers that are <em>not</em> the SCTracker plugin — currently the
     * GW Launcher Reforged (GWRL) launcher hitting {@code GET /module-entitlements} and
     * {@code GET /modules/{key}/download}. Skips both extras {@link #authenticate} does: no
     * {@code recordPluginSeen} (GWRL sends no {@code X-Plugin-Version}; stamping null would corrupt
     * the "Players On An Outdated Plugin" signal) and no
     * {@link PluginVersionMetadataLoader#requireCurrentVersion} (GWRL versions independently of
     * SCTracker and must never hit its 426 gate).
     *
     * @return the fully-loaded {@link Person} the raw machine key belongs to; 401 if the key is
     *         missing, blank, unknown, or revoked.
     */
    public Person authenticateWithoutVersionCheck(String rawMachineKey) {
        return loadPerson(lookupMachineKey(rawMachineKey));
    }

    /**
     * Same as {@link #authenticate}, but also records a rejected attempt (backing the "most
     * outdated-plugin upload attempts by user" loserboard) when the version check fails. Only
     * /upload-run uses this variant — that's the metric being tracked, not every
     * machine-key-authenticated endpoint (e.g. the failure-report permission check would otherwise
     * inflate counts every time an outdated plugin merely loads).
     */
    public Person authenticateForUpload(String rawMachineKey, Integer pluginVersion) {
        MachineKey machineKey = lookupMachineKey(rawMachineKey);
        recordPluginSeen(machineKey, pluginVersion);
        try {
            pluginVersionMetadataLoader.requireCurrentVersion(pluginVersion);
        } catch (ApiException e) {
            outdatedUploadAttemptRepository.save(new OutdatedUploadAttempt(machineKey.getPerson().getId(), pluginVersion));
            throw e;
        }
        return loadPerson(machineKey);
    }

    // Stamped for every machine-key request (all four endpoints), before the version check so an
    // already-outdated caller that's about to 426 still registers as active. machineKey.getPerson()
    // is a lazy proxy but .getId() resolves without a DB hit. Backs the "Players On An Outdated
    // Plugin" Loserboards widget — the only per-user signal an outdated plugin still emits is its
    // once-per-load GET /can-report-run-failure, which carries both the key and X-Plugin-Version.
    private void recordPluginSeen(MachineKey machineKey, Integer pluginVersion) {
        personRepository.recordPluginSeen(machineKey.getPerson().getId(), Instant.now(), pluginVersion);
    }

    private MachineKey lookupMachineKey(String rawMachineKey) {
        if (rawMachineKey == null || rawMachineKey.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "missing X-Machine-Key");
        }
        String hash = MachineKeyHasher.hash(rawMachineKey);
        return machineKeyRepository.findByKeyHashAndRevokedAtIsNull(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid or revoked machine key"));
    }

    // machineKey was loaded outside any transaction (open-in-view is disabled, and each repository
    // call auto-commits its own), so machineKey.getPerson() is an uninitialized lazy proxy — fine
    // for .getId() (Hibernate resolves that without a DB hit) but not for reading an actual field.
    // findById runs its own self-contained transaction and returns a fully-loaded entity, safe to
    // read from afterward.
    private Person loadPerson(MachineKey machineKey) {
        return personRepository.findById(machineKey.getPerson().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid or revoked machine key"));
    }
}
