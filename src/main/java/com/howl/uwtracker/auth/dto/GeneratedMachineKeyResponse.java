package com.howl.uwtracker.auth.dto;

/** The raw key is present here only — never stored, never returned again after this response. */
public record GeneratedMachineKeyResponse(Long id, String key, String label) {
}
