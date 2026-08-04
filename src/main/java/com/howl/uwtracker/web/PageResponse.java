package com.howl.uwtracker.web;

import org.springframework.data.domain.Page;

import java.util.List;

/** Pagination envelope from specs/backend/00-overview.md, shared by any future paginated endpoint. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
