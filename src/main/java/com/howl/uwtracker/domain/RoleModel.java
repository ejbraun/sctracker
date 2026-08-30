package com.howl.uwtracker.domain;

import java.util.Arrays;

/**
 * How {@code run_participants.role} is derived for a run, chosen per {@code (map, party_size)} via
 * {@link MapConfig#getRoleModel()} — see specs/features/fow-and-party-size.md.
 *
 * <ul>
 *   <li>{@link #TRAPPER} — the Underworld 8-man scheme: Elvar override, then plugin {@code role_hint}
 *       for T1/T2/T3, then elimination, then the ordered (primary, secondary) profession-combo
 *       table. Only ever configured for {@code (UW, 8)}.</li>
 *   <li>{@link #PRIMARY_PROFESSION} — the Fissure of Woe duo scheme: the role is simply the primary
 *       profession's name (Ranger-primary → {@code Ranger}, Dervish-primary → {@code Derv}).</li>
 * </ul>
 *
 * <p>A {@code (map, party_size)} config with a {@code NULL} {@code role_model} has no model at all:
 * every participant's role stays {@code null} and the personal section-best query is not role-gated.
 */
public enum RoleModel {

    TRAPPER("trapper"),
    PRIMARY_PROFESSION("primary_profession");

    private final String wireValue;

    RoleModel(String wireValue) {
        this.wireValue = wireValue;
    }

    /** The string stored in {@code map_configs.role_model} (kept out of the enum constant name so it stays Java-conventional). */
    public String wireValue() {
        return wireValue;
    }

    /** @return the matching model, or {@code null} for a {@code null}/blank value; throws on an unrecognised non-blank value. */
    public static RoleModel fromWire(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(m -> m.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown role_model: " + value));
    }
}
