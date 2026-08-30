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
    void listsTheCuratedSetOfSupportedMapsWithTheirConfigs() throws Exception {
        MockHttpSession session = signup("mapviewer", "password123");

        List<MapResponse> maps = fetchMaps(session);

        assertThat(maps).hasSize(1);
        assertThat(maps.get(0).id()).isEqualTo(UNDERWORLD_MAP_ID);
        assertThat(maps.get(0).name()).isEqualTo(UNDERWORLD_MAP_NAME);
        assertThat(maps.get(0).configs()).singleElement().satisfies(c -> {
            assertThat(c.partySize()).isEqualTo(8);
            assertThat(c.roleModel()).isEqualTo("trapper");
        });
    }

    @Test
    void includesTheFissureOfWoeConfigsOnceSeeded() throws Exception {
        seedFissureOfWoe();
        MockHttpSession session = signup("fowmapviewer", "password123");

        List<MapResponse> maps = fetchMaps(session);

        assertThat(maps).hasSize(2);
        MapResponse fow = maps.stream().filter(m -> m.id() == FISSURE_OF_WOE_MAP_ID).findFirst().orElseThrow();
        assertThat(fow.name()).isEqualTo(FISSURE_OF_WOE_MAP_NAME);
        // Ascending by party size: the role-gated duo, then the role-less 8-man.
        assertThat(fow.configs()).hasSize(2);
        assertThat(fow.configs().get(0).partySize()).isEqualTo(2);
        assertThat(fow.configs().get(0).roleModel()).isEqualTo("primary_profession");
        assertThat(fow.configs().get(1).partySize()).isEqualTo(8);
        assertThat(fow.configs().get(1).roleModel()).isNull();
    }

    private List<MapResponse> fetchMaps(MockHttpSession session) throws Exception {
        String body = mockMvc.perform(get("/api/maps").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body,
                objectMapper.getTypeFactory().constructCollectionType(List.class, MapResponse.class));
    }

    @Test
    void requiresAnActiveSession() throws Exception {
        mockMvc.perform(get("/api/maps"))
                .andExpect(status().isUnauthorized());
    }
}
