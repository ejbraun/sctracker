package com.howl.uwtracker.ingestion.dto;

/** indent — not in the original spec draft, found in a real payload sample; nesting depth, stored for fidelity. */
public record ObjectiveDto(String name, Integer status, Long start, Long done, Long duration, Integer indent) {
}
