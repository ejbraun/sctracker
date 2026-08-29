package com.howl.uwtracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.howl.uwtracker.auth.dto.GeneratedMachineKeyResponse;
import com.howl.uwtracker.auth.dto.LoginRequest;
import com.howl.uwtracker.auth.dto.SignupRequest;
import com.howl.uwtracker.domain.SignupKey;
import com.howl.uwtracker.repository.GameMapRepository;
import com.howl.uwtracker.repository.MachineKeyRepository;
import com.howl.uwtracker.repository.PersonRepository;
import com.howl.uwtracker.repository.PlayerCharacterRepository;
import com.howl.uwtracker.repository.ProfessionRepository;
import com.howl.uwtracker.repository.RoleObjectiveRepository;
import com.howl.uwtracker.repository.RunObjectiveRepository;
import com.howl.uwtracker.repository.RunParticipantItemDropRepository;
import com.howl.uwtracker.repository.RunParticipantRepository;
import com.howl.uwtracker.repository.RunRepository;
import com.howl.uwtracker.repository.SignupKeyRepository;
import com.howl.uwtracker.repository.TrackedItemRepository;
import com.howl.uwtracker.web.MachineKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for every backend integration test. Boots the real Spring context (Liquibase changesets
 * included — this is the first thing in the project that actually runs them against a live MySQL,
 * per IMPLEMENTATION_PROGRESS.md's "no Docker in this working environment" gap) against a MySQL
 * container matching docker-compose.yml's image.
 *
 * <p>The container is a singleton: one {@code static} field on this base class, started once (in the
 * static initializer below) and shared by every subclass for the whole test run, rather than one
 * container per test class. {@code @ServiceConnection} wires the datasource properties automatically
 * — no manual {@code @DynamicPropertySource} needed.
 *
 * <p><b>Deliberately not annotated {@code @Container} / class not annotated {@code @Testcontainers}.</b>
 * That combination looks like the right way to share a container across test classes, but isn't:
 * the JUnit5 Testcontainers extension calls {@code stop()} on a {@code @Container}-annotated field in
 * an {@code @AfterAll}-equivalent hook scoped to whichever test class happens to be finishing — since
 * every subclass shares the same static field, this stops the container after the *first* test class
 * completes, and the *next* class's {@code @BeforeAll} restarts it as a brand-new container (new
 * random port). Spring's test-context cache doesn't know that happened — it reuses the
 * {@code ApplicationContext} (and its {@code HikariPool}) from the first class, which still points at
 * the now-dead old port, producing exactly the kind of connection failures this class exists to avoid.
 * Starting it ourselves, once, with no {@code @Container} annotation for the extension to manage,
 * avoids this entirely; Ryuk still reaps the container at JVM exit either way.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    static {
        MYSQL.start();
    }

    /** The default supported map, matching 011-seed-supported-maps.xml + its (8, trapper) map_configs row. */
    public static final int UNDERWORLD_MAP_ID = 72;
    public static final String UNDERWORLD_MAP_NAME = "Underworld";

    /** The Fissure of Woe (038-seed-fow.xml) — a 2-person duo, primary_profession role model.
     *  Not reseeded by cleanDatabase(); a test that needs it calls {@link #seedFissureOfWoe()}. */
    public static final int FISSURE_OF_WOE_MAP_ID = 34;
    public static final String FISSURE_OF_WOE_MAP_NAME = "The Fissure of Woe";

    // Children first, respecting FK order; role_objectives/people have no dependents left after this.
    // "maps" truncates like everything else, but — unlike everything else — cleanDatabase() below
    // reseeds it with the curated set (011-seed-supported-maps.xml) right after: maps is reference
    // data, not per-test fixture data, so every test should start from that canonical state, not an
    // empty table. ("professions"/"tracked_items" don't need this: nothing ever deletes rows from
    // them, so they're not in this list at all — unlike "maps," they also have no FK-order reason to
    // be, since nothing but seed migrations ever writes to them.) "run_participant_item_drops",
    // "run_failure_reasons", and "run_mvp_awards" must all truncate before "run_participants"/"runs":
    // TRUNCATE doesn't fire ON DELETE CASCADE, and run_participants'/runs' auto-increment resets after
    // truncation — leftover rows in any of these would silently attach to whatever unrelated
    // participant/run reuses that id in a later test otherwise (run_failure_reasons was missing from
    // this list entirely until the run_mvp_awards addition surfaced the same latent bug in its sibling).
    private static final List<String> TABLES_TO_CLEAN = List.of(
            "run_participant_item_drops", "run_failure_reasons", "run_mvp_awards", "run_participants",
            "run_objectives", "runs", "role_objectives", "characters", "machine_keys", "signup_keys",
            "admins", "people", "map_configs", "maps");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected PersonRepository personRepository;

    @Autowired
    protected PlayerCharacterRepository playerCharacterRepository;

    @Autowired
    protected ProfessionRepository professionRepository;

    @Autowired
    protected GameMapRepository gameMapRepository;

    @Autowired
    protected RunRepository runRepository;

    @Autowired
    protected RunObjectiveRepository runObjectiveRepository;

    @Autowired
    protected RunParticipantRepository runParticipantRepository;

    @Autowired
    protected RoleObjectiveRepository roleObjectiveRepository;

    @Autowired
    protected MachineKeyRepository machineKeyRepository;

    @Autowired
    protected SignupKeyRepository signupKeyRepository;

    @Autowired
    protected RunParticipantItemDropRepository runParticipantItemDropRepository;

    @Autowired
    protected TrackedItemRepository trackedItemRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        for (String table : TABLES_TO_CLEAN) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
        jdbcTemplate.update("INSERT INTO maps (id, name) VALUES (?, ?)", UNDERWORLD_MAP_ID, UNDERWORLD_MAP_NAME);
        jdbcTemplate.update("INSERT INTO map_configs (map_id, party_size, role_model) VALUES (?, 8, 'trapper')", UNDERWORLD_MAP_ID);
    }

    /**
     * Seeds The Fissure of Woe (map + its 2-person, primary_profession {@code map_configs} row +
     * the duo role_objectives rows changeset 039 provides), for tests that exercise the FoW path.
     * Not part of {@link #cleanDatabase()} since most tests don't need it.
     */
    protected void seedFissureOfWoe() {
        jdbcTemplate.update("INSERT INTO maps (id, name) VALUES (?, ?)", FISSURE_OF_WOE_MAP_ID, FISSURE_OF_WOE_MAP_NAME);
        jdbcTemplate.update("INSERT INTO map_configs (map_id, party_size, role_model) VALUES (?, 2, 'primary_profession')",
                FISSURE_OF_WOE_MAP_ID);
        // The 8-man config has no role model (FoW 8-man has no fixed composition).
        jdbcTemplate.update("INSERT INTO map_configs (map_id, party_size, role_model) VALUES (?, 8, NULL)",
                FISSURE_OF_WOE_MAP_ID);
        for (String objective : List.of("ToC", "Wailing Lord", "Griffons", "Defend", "Forge", "Menzies",
                "Restore", "Khobay", "ToS", "Burning Forest", "The Hunt")) {
            jdbcTemplate.update("INSERT INTO role_objectives (map_id, objective_name, role) VALUES (?, ?, 'Ranger')",
                    FISSURE_OF_WOE_MAP_ID, objective);
            jdbcTemplate.update("INSERT INTO role_objectives (map_id, objective_name, role) VALUES (?, ?, 'Derv')",
                    FISSURE_OF_WOE_MAP_ID, objective);
        }
    }

    /**
     * Signs up a fresh person and returns the {@link MockHttpSession} the signup response
     * established. Mints its own single-use signup key directly (bypassing the finite pool a real
     * deployment hands out) so callers don't have to think about signup-key plumbing at all.
     */
    protected MockHttpSession signup(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/signup")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(username, password, freshSignupKey()))))
                .andExpect(status().isCreated());
        return session;
    }

    /** Inserts a fresh unused signup key directly and returns its plaintext. */
    protected String freshSignupKey() {
        String rawKey = MachineKeyHasher.generateRawKey();
        signupKeyRepository.save(new SignupKey(MachineKeyHasher.hash(rawKey)));
        return rawKey;
    }

    /** Logs into an existing account and returns the {@link MockHttpSession} the login response established. */
    protected MockHttpSession login(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk());
        return session;
    }

    /** Generates a machine key for the already-authenticated {@code session} and returns the one-time raw key. */
    protected String generateMachineKey(MockHttpSession session, String label) throws Exception {
        String body = mockMvc.perform(post("/api/account/machine-keys")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.howl.uwtracker.auth.dto.GenerateMachineKeyRequest(label))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, GeneratedMachineKeyResponse.class).key();
    }

    /** Grants admin status directly — the only way to, per {@code Admin}'s class doc (no API writes this table). */
    protected void makeAdmin(Long personId) {
        jdbcTemplate.update("INSERT INTO admins (person_id) VALUES (?)", personId);
    }
}
