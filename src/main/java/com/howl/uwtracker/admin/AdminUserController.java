package com.howl.uwtracker.admin;

import com.howl.uwtracker.admin.dto.AdminUserModuleResponse;
import com.howl.uwtracker.admin.dto.AdminUserResponse;
import com.howl.uwtracker.admin.dto.SetCanReportFailuresRequest;
import com.howl.uwtracker.auth.CurrentPersonId;
import com.howl.uwtracker.characters.dto.CharacterResponse;
import com.howl.uwtracker.characters.dto.CreateCharacterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin-only "User Management" — protected by {@link com.howl.uwtracker.auth.AdminAuthInterceptor}. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> list() {
        return ResponseEntity.ok(adminUserService.list());
    }

    @PatchMapping("/{personId}/can-report-failures")
    public ResponseEntity<AdminUserResponse> setCanReportFailures(@PathVariable Long personId,
                                                                    @RequestBody SetCanReportFailuresRequest request) {
        return ResponseEntity.ok(adminUserService.setCanReportFailures(personId, request.canReportFailures()));
    }

    @GetMapping("/{personId}/characters")
    public ResponseEntity<List<CharacterResponse>> listCharacters(@PathVariable Long personId) {
        return ResponseEntity.ok(adminUserService.listCharacters(personId));
    }

    @PostMapping("/{personId}/characters")
    public ResponseEntity<CharacterResponse> addCharacter(@PathVariable Long personId,
                                                           @RequestBody CreateCharacterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUserService.addCharacter(personId, request.characterName()));
    }

    @GetMapping("/{personId}/modules")
    public ResponseEntity<List<AdminUserModuleResponse>> listModules(@PathVariable Long personId) {
        return ResponseEntity.ok(adminUserService.listModules(personId));
    }

    @PutMapping("/{personId}/modules/{moduleKey}")
    public ResponseEntity<Void> grantModule(@CurrentPersonId Long adminPersonId,
                                            @PathVariable Long personId, @PathVariable String moduleKey) {
        adminUserService.grantModule(personId, moduleKey, adminPersonId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{personId}/modules/{moduleKey}")
    public ResponseEntity<Void> revokeModule(@PathVariable Long personId, @PathVariable String moduleKey) {
        adminUserService.revokeModule(personId, moduleKey);
        return ResponseEntity.noContent().build();
    }
}
