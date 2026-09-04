package com.howl.uwtracker.modules;

import com.howl.uwtracker.domain.Module;
import com.howl.uwtracker.domain.ModuleType;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.modules.dto.ModuleEntitlementsResponse;
import com.howl.uwtracker.modules.dto.ModuleEntitlementsResponse.Entry;
import com.howl.uwtracker.plugin.PluginVersionMetadata;
import com.howl.uwtracker.repository.ModuleRepository;
import com.howl.uwtracker.repository.PersonModuleGrantRepository;
import com.howl.uwtracker.web.MachineKeyAuthenticationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Resolves {@code GET /module-entitlements}: authenticate the machine key (no plugin-version gate),
 * then return every enabled module that is public or granted to that person. Entitlement is a live
 * DB read — a grant or revoke is visible on the very next call.
 */
@Service
public class ModuleEntitlementService {

    private final ModuleRepository moduleRepository;
    private final PersonModuleGrantRepository grantRepository;
    private final MachineKeyAuthenticationService machineKeyAuth;
    private final ModuleManifestResolver manifestResolver;

    public ModuleEntitlementService(ModuleRepository moduleRepository, PersonModuleGrantRepository grantRepository,
                                    MachineKeyAuthenticationService machineKeyAuth,
                                    ModuleManifestResolver manifestResolver) {
        this.moduleRepository = moduleRepository;
        this.grantRepository = grantRepository;
        this.machineKeyAuth = machineKeyAuth;
        this.manifestResolver = manifestResolver;
    }

    /** @param type optional filter — {@code null} returns every entitled module regardless of kind. */
    public ModuleEntitlementsResponse forMachineKey(String rawMachineKey, ModuleType type) {
        Person person = machineKeyAuth.authenticateWithoutVersionCheck(rawMachineKey); // 401
        return forPerson(person.getId(), type);
    }

    /**
     * Same entitlement resolution as {@link #forMachineKey}, keyed off an already-authenticated
     * person id — the session-authenticated {@code GET /api/account/modules} path. Entitlement is a
     * live DB read here too.
     */
    public ModuleEntitlementsResponse forPerson(Long personId, ModuleType type) {
        Set<Long> granted = grantRepository.findModuleIdsByPersonId(personId);

        List<Entry> modules = moduleRepository.findByEnabledTrueOrderBySortOrderAscModuleKeyAsc().stream()
                .filter(module -> module.isPublicAccess() || granted.contains(module.getId()))
                .filter(module -> type == null || module.getType() == type)
                .map(this::toEntry)
                .toList();
        return new ModuleEntitlementsResponse(modules);
    }

    private Entry toEntry(Module module) {
        PluginVersionMetadata manifest = manifestResolver.manifestFor(module);
        return new Entry(
                module.getModuleKey(),
                module.getDisplayName(),
                module.getType(),
                module.isPublicAccess(),
                manifest != null ? manifest.version() : module.getCurrentVersion(),
                manifest != null ? manifest.sha256() : module.getCurrentSha256(),
                ModuleMetadataService.downloadUrl(module.getModuleKey()));
    }
}
