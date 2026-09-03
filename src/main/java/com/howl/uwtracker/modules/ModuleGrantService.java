package com.howl.uwtracker.modules;

import com.howl.uwtracker.admin.dto.AdminUserModuleResponse;
import com.howl.uwtracker.domain.Module;
import com.howl.uwtracker.domain.PersonModuleGrant;
import com.howl.uwtracker.domain.PersonModuleGrantId;
import com.howl.uwtracker.repository.ModuleRepository;
import com.howl.uwtracker.repository.PersonModuleGrantRepository;
import com.howl.uwtracker.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Per-user module grants — the backing logic for the {@code /api/admin/users/{personId}/modules}
 * sub-resource. {@link com.howl.uwtracker.admin.AdminUserService} delegates here (it checks the
 * user exists first), the same way it delegates character management to {@code CharacterService}.
 */
@Service
public class ModuleGrantService {

    private final ModuleRepository moduleRepository;
    private final PersonModuleGrantRepository grantRepository;

    public ModuleGrantService(ModuleRepository moduleRepository, PersonModuleGrantRepository grantRepository) {
        this.moduleRepository = moduleRepository;
        this.grantRepository = grantRepository;
    }

    /** Every enabled module with whether {@code personId} currently holds a grant for it. */
    @Transactional(readOnly = true)
    public List<AdminUserModuleResponse> listForUser(Long personId) {
        Map<Long, PersonModuleGrant> grantsByModuleId = grantRepository.findByIdPersonId(personId).stream()
                .collect(Collectors.toMap(g -> g.getId().getModuleId(), Function.identity()));

        return moduleRepository.findByEnabledTrueOrderBySortOrderAscModuleKeyAsc().stream()
                .map(module -> {
                    PersonModuleGrant grant = grantsByModuleId.get(module.getId());
                    return new AdminUserModuleResponse(
                            module.getModuleKey(), module.getDisplayName(), module.isPublicAccess(),
                            grant != null,
                            grant == null ? null : grant.getGrantedAt(),
                            grant == null ? null : grant.getGrantedBy());
                })
                .toList();
    }

    /** Idempotent — granting an already-granted module is a no-op (keeps the original grantedBy/at). */
    @Transactional
    public void grant(Long personId, String moduleKey, Long grantedByPersonId) {
        Module module = requireModule(moduleKey);
        if (!grantRepository.existsByIdPersonIdAndIdModuleId(personId, module.getId())) {
            grantRepository.save(new PersonModuleGrant(
                    new PersonModuleGrantId(personId, module.getId()), grantedByPersonId));
        }
    }

    /** Idempotent — revoking a module that isn't granted is a no-op. */
    @Transactional
    public void revoke(Long personId, String moduleKey) {
        Module module = requireModule(moduleKey);
        grantRepository.deleteByIdPersonIdAndIdModuleId(personId, module.getId());
    }

    private Module requireModule(String moduleKey) {
        return moduleRepository.findByModuleKey(moduleKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "unknown module"));
    }
}
