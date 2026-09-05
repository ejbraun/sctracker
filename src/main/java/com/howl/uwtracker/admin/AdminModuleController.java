package com.howl.uwtracker.admin;

import com.howl.uwtracker.admin.dto.AdminModuleResponse;
import com.howl.uwtracker.admin.dto.BucketScanResponse;
import com.howl.uwtracker.admin.dto.CreateModuleRequest;
import com.howl.uwtracker.admin.dto.UpdateModuleRequest;
import com.howl.uwtracker.modules.ModuleAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin-only registry management — protected by {@link com.howl.uwtracker.auth.AdminAuthInterceptor}. */
@RestController
@RequestMapping("/api/admin/modules")
public class AdminModuleController {

    private final ModuleAdminService moduleAdminService;

    public AdminModuleController(ModuleAdminService moduleAdminService) {
        this.moduleAdminService = moduleAdminService;
    }

    @GetMapping
    public ResponseEntity<List<AdminModuleResponse>> list() {
        return ResponseEntity.ok(moduleAdminService.list());
    }

    /**
     * Bucket scan: {@code discovered} is {@code plugins/<Folder>/} directories with a dll but no
     * registry row yet; {@code updates} is existing modules whose folder has a manifest/patch-notes
     * file the row doesn't reference yet.
     */
    @GetMapping("/discover")
    public ResponseEntity<BucketScanResponse> discover() {
        return ResponseEntity.ok(moduleAdminService.discover());
    }

    @PostMapping
    public ResponseEntity<AdminModuleResponse> create(@RequestBody CreateModuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moduleAdminService.create(request));
    }

    @PatchMapping("/{moduleKey}")
    public ResponseEntity<AdminModuleResponse> update(@PathVariable String moduleKey,
                                                     @RequestBody UpdateModuleRequest request) {
        return ResponseEntity.ok(moduleAdminService.update(moduleKey, request));
    }

    @DeleteMapping("/{moduleKey}")
    public ResponseEntity<Void> delete(@PathVariable String moduleKey) {
        moduleAdminService.delete(moduleKey);
        return ResponseEntity.noContent().build();
    }
}
