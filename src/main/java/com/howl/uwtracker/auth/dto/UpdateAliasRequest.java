package com.howl.uwtracker.auth.dto;

/** {@code alias} may be null/blank to clear a previously-set alias. */
public record UpdateAliasRequest(String alias) {
}
