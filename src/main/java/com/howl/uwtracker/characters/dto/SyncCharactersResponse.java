package com.howl.uwtracker.characters.dto;

import java.util.List;

/**
 * Body of {@code POST /sync-characters}. {@code added} is exactly the submitted names that were
 * newly registered this call (in submission order) — a name already registered, to this person or
 * anyone else, is silently skipped and not listed here (never reassigned, and deliberately not
 * distinguished in the response which of the two happened).
 */
public record SyncCharactersResponse(List<String> added) {
}
