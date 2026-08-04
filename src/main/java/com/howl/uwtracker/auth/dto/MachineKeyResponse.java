package com.howl.uwtracker.auth.dto;

import com.howl.uwtracker.domain.MachineKey;

import java.time.Instant;

public record MachineKeyResponse(Long id, String label, Instant createdAt, Instant revokedAt) {

    public static MachineKeyResponse from(MachineKey key) {
        return new MachineKeyResponse(key.getId(), key.getLabel(), key.getCreatedAt(), key.getRevokedAt());
    }
}
