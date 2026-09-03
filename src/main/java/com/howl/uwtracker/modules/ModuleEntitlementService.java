package com.howl.uwtracker.modules;

import com.howl.uwtracker.domain.Module;
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

    public ModuleEntitlementsResponse forMachineKey(String rawMachineKey) {
        Person person = machineKeyAuth.authenticateWithoutVersionCheck(rawMachineKey); // 401
        Set<Long> granted = grantRepository.findModuleIdsByPersonId(person.getId());

        List<Entry> modules = moduleRepository.findByEnabledTrueOrderBySortOrderAscModuleKeyAsc().stream()
                .filter(module -> module.isPublicAccess() || granted.contains(module.getId()))
                .map(this::toEntry)
                .toList();
        return new ModuleEntitlementsResponse(modules);
    }

    private Entry toEntry(Module module) {
        PluginVersionMetadata manifest = manifestResolver.manifestFor(module);
        return new Entry(
                module.getModuleKey(),
                module.getDisplayName(),
                module.isPublicAccess(),
                manifest != null ? manifest.version() : module.getCurrentVersion(),
                manifest != null ? manifest.sha256() : module.getCurrentSha256(),
                ModuleMetadataService.downloadUrl(module.getModuleKey()));
    }
}
