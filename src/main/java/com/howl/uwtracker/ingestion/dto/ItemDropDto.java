package com.howl.uwtracker.ingestion.dto;

/**
 * One element of a party member's {@code item_drops[]} — {@code id} is the item's raw model id
 * (GW::Constants::ItemID), not a display name; {@code count} is how many times that item was
 * reserved for this member during the run. See PartyMemberDto for the field's overall shape.
 */
public record ItemDropDto(Integer id, Integer count) {
}
