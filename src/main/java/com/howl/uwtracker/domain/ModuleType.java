package com.howl.uwtracker.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * What a {@code modules} row is, so a consumer can ask for just its kind (the {@code ?type=} query
 * param on {@code /artifacts} and {@code /module-entitlements}):
 *
 * <ul>
 *   <li>{@link #PLUGIN} — a GWToolbox++ plugin DLL (SCTracker, and GWRL features that run inside
 *       the toolbox). Published under {@code plugins/<Name>/}.</li>
 *   <li>{@link #MODULE} — a GW Launcher Reforged (GWRL) launcher component, loaded by the launcher
 *       itself rather than the toolbox. Published under {@code launcher/<Name>/} — see
 *       specs/integrations/gw-launcher-reforged.md §7.</li>
 * </ul>
 *
 * <p>Stored lowercase in {@code modules.type} (via {@link ModuleTypeConverter}); same string on the
 * JSON wire and the query param. The constant name is kept Java-conventional, per {@link RoleModel}.
 */
public enum ModuleType {

    PLUGIN("plugin"),
    MODULE("module");

    private final String wireValue;

    ModuleType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /** @throws IllegalArgumentException on an unrecognised value (→ 400 at request binding). */
    @JsonCreator
    public static ModuleType fromWire(String value) {
        return Arrays.stream(values())
                .filter(t -> t.wireValue.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown module type: " + value));
    }
}
