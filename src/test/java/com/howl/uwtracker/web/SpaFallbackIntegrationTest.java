package com.howl.uwtracker.web;

import com.howl.uwtracker.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;

/**
 * specs/backend/00-overview.md's SPA fallback — deep-link requests (no dot in the last segment, not
 * under /api or /upload-run) forward to index.html so React Router can render them. This is the
 * mapping whose {@code /**{@literal /}{path:[^.]*}} pattern is only valid under the legacy
 * AntPathMatcher (see application.properties' {@code spring.mvc.pathmatch.matching-strategy}) —
 * Spring Boot 3's default PathPatternParser rejects it at startup outright, which nothing caught
 * before this project's first real web-context-booting integration test.
 */
class SpaFallbackIntegrationTest extends AbstractIntegrationTest {

    @Test
    void forwardsATopLevelDeepLinkToIndexHtml() throws Exception {
        mockMvc.perform(get("/runs"))
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void forwardsANestedDeepLinkToIndexHtml() throws Exception {
        mockMvc.perform(get("/runs/42"))
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void doesNotShadowApiRoutes() throws Exception {
        // /api/account/me exists as a real controller mapping — it must win over the catch-all and
        // return its own (401, unauthenticated) response, not a forwarded index.html.
        mockMvc.perform(get("/api/account/me"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }
}
