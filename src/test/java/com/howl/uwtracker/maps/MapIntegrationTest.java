package com.howl.uwtracker.maps;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.maps.dto.MapResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * specs/backend/00-overview.md's "Reference data" section — GET /api/maps against real MySQL.
 * maps is now a curated, well-defined set (011-seed-supported-maps.xml — see AbstractIntegrationTest,
 * which reseeds it before every test), not auto-discovered from arbitrary uploaded map_ids, so there's
 * no code path that produces a map with a null name anymore.
 */
class MapIntegrationTest extends AbstractIntegrationTest {

    @Test
    void listsTheCuratedSetOfSupportedMaps() throws Exception {
        MockHttpSession session = signup("mapviewer", "password123");

        String body = mockMvc.perform(get("/api/maps").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<MapResponse> maps = objectMapper.readValue(body,
                objectMapper.getTypeFactory().constructCollectionType(List.class, MapResponse.class));

        assertThat(maps).hasSize(1);
        assertThat(maps.get(0).id()).isEqualTo(UNDERWORLD_MAP_ID);
        assertThat(maps.get(0).name()).isEqualTo(UNDERWORLD_MAP_NAME);
    }

    @Test
    void requiresAnActiveSession() throws Exception {
        mockMvc.perform(get("/api/maps"))
                .andExpect(status().isUnauthorized());
    }
}
