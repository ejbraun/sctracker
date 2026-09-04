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
        // The full 1-8 range, ascending by party size. Only the size-2 duo is role-gated; every
        // other size is role-less (role_model = NULL).
        assertThat(fow.configs()).extracting(c -> c.partySize())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(fow.configs()).filteredOn(c -> c.roleModel() != null)
                .singleElement().satisfies(c -> {
                    assertThat(c.partySize()).isEqualTo(2);
                    assertThat(c.roleModel()).isEqualTo("primary_profession");
                });
    }

    @Test
    void includesTheDomainOfAnguishConfigOnceSeeded() throws Exception {
        seedDomainOfAnguish();
        MockHttpSession session = signup("doamapviewer", "password123");

        List<MapResponse> maps = fetchMaps(session);

        assertThat(maps).hasSize(2);
        MapResponse doa = maps.stream().filter(m -> m.id() == DOMAIN_OF_ANGUISH_MAP_ID).findFirst().orElseThrow();
        assertThat(doa.name()).isEqualTo(DOMAIN_OF_ANGUISH_MAP_NAME);
        // A single 8-man config with no role model (role-less, like FoW 8-man).
        assertThat(doa.configs()).singleElement().satisfies(c -> {
            assertThat(c.partySize()).isEqualTo(8);
            assertThat(c.roleModel()).isNull();
        });
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
