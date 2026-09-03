package com.howl.uwtracker.admin.dto;

/** Body of {@code PATCH /api/admin/users/{personId}/admin} — wire: {@code {"is_admin": true|false}}. */
public record SetAdminRequest(boolean isAdmin) {
}
