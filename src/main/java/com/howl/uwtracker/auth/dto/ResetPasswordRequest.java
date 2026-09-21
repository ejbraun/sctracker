package com.howl.uwtracker.auth.dto;

public record ResetPasswordRequest(String token, String password) {
}
