package com.howl.uwtracker.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Machine keys (and signup keys — see {@code AuthService.signup}) are high-entropy generated
 * secrets, not user-chosen passwords, so a fast deterministic SHA-256 lookup is appropriate here —
 * unlike Person passwords, which use BCrypt (spec 03). Shared by ingestion (spec 02, verifying),
 * account machine-key management (spec 03, generating), and signup-key verification — kept in
 * com.howl.uwtracker.web rather than any one feature package for that reason.
 */
public final class MachineKeyHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    private MachineKeyHasher() {
    }

    public static String generateRawKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
