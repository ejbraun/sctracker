package com.howl.uwtracker.auth;

import com.howl.uwtracker.auth.dto.GenerateMachineKeyRequest;
import com.howl.uwtracker.auth.dto.GeneratedMachineKeyResponse;
import com.howl.uwtracker.auth.dto.MachineKeyResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** specs/backend/03-auth.md — machine-key self-service. Protected by SessionAuthInterceptor (under /api/**). */
@RestController
@RequestMapping("/api/account/machine-keys")
public class MachineKeyController {

    private final MachineKeyService machineKeyService;

    public MachineKeyController(MachineKeyService machineKeyService) {
        this.machineKeyService = machineKeyService;
    }

    @PostMapping
    public ResponseEntity<GeneratedMachineKeyResponse> generate(@CurrentPersonId Long personId,
                                                                  @RequestBody GenerateMachineKeyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(machineKeyService.generate(personId, request.label()));
    }

    @GetMapping
    public ResponseEntity<List<MachineKeyResponse>> list(@CurrentPersonId Long personId) {
        List<MachineKeyResponse> keys = machineKeyService.list(personId).stream()
                .map(MachineKeyResponse::from)
                .toList();
        return ResponseEntity.ok(keys);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@CurrentPersonId Long personId, @PathVariable Long id) {
        machineKeyService.revoke(personId, id);
        return ResponseEntity.noContent().build();
    }
}
