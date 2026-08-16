package com.howl.uwtracker.web;

import com.howl.uwtracker.domain.MachineKey;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.plugin.PluginVersionMetadataLoader;
import com.howl.uwtracker.repository.MachineKeyRepository;
import com.howl.uwtracker.repository.PersonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Shared X-Machine-Key authentication for every machine-key-authenticated endpoint (currently
 * /upload-run, /report-run-failure, /can-report-run-failure) — kept in com.howl.uwtracker.web
 * rather than any one feature package, same reasoning as {@link MachineKeyHasher}.
 */
@Service
public class MachineKeyAuthenticationService {

    private final MachineKeyRepository machineKeyRepository;
    private final PersonRepository personRepository;
    private final PluginVersionMetadataLoader pluginVersionMetadataLoader;

    public MachineKeyAuthenticationService(MachineKeyRepository machineKeyRepository, PersonRepository personRepository,
                                            PluginVersionMetadataLoader pluginVersionMetadataLoader) {
        this.machineKeyRepository = machineKeyRepository;
        this.personRepository = personRepository;
        this.pluginVersionMetadataLoader = pluginVersionMetadataLoader;
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
        if (rawMachineKey == null || rawMachineKey.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "missing X-Machine-Key");
        }
        String hash = MachineKeyHasher.hash(rawMachineKey);
        MachineKey machineKey = machineKeyRepository.findByKeyHashAndRevokedAtIsNull(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid or revoked machine key"));

        pluginVersionMetadataLoader.requireCurrentVersion(pluginVersion);

        // machineKey was loaded outside any transaction (open-in-view is disabled, and each
        // repository call auto-commits its own), so machineKey.getPerson() is an uninitialized lazy
        // proxy — fine for .getId() (Hibernate resolves that without a DB hit) but not for reading an
        // actual field. findById runs its own self-contained transaction and returns a fully-loaded
        // entity, safe to read from afterward.
        return personRepository.findById(machineKey.getPerson().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid or revoked machine key"));
    }
}
