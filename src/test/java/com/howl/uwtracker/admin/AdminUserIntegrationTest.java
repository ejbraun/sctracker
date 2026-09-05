package com.howl.uwtracker.admin;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.characters.dto.CreateCharacterRequest;
import com.howl.uwtracker.domain.GameMap;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.PlayerCharacter;
import com.howl.uwtracker.domain.Profession;
import com.howl.uwtracker.domain.Run;
import com.howl.uwtracker.domain.RunParticipant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin "User Management" — the per-user character view + register-on-behalf endpoints. */
class AdminUserIntegrationTest extends AbstractIntegrationTest {

    private MockHttpSession adminSession() throws Exception {
        String username = "admin-" + System.nanoTime();
        MockHttpSession session = signup(username, "password123");
        makeAdmin(personRepository.findByUsername(username).orElseThrow().getId());
        return session;
    }

    private Person user(String username) {
        return personRepository.save(new Person(username, "irrelevant-hash"));
    }

    @Test
    void listIncludesEachUsersCreatedAt() throws Exception {
        MockHttpSession admin = adminSession();
        String username = "created-at-" + System.nanoTime();
        user(username);

        String body = mockMvc.perform(get("/api/admin/users").session(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var node = java.util.stream.StreamSupport.stream(objectMapper.readTree(body).spliterator(), false)
                .filter(n -> n.get("username").asText().equals(username))
                .findFirst().orElseThrow();
        assertThat(node.get("created_at").asText()).isNotBlank();
    }

    @Test
    void listsAUsersCharactersSortedByName() throws Exception {
        MockHttpSession admin = adminSession();
        Person target = user("target-" + System.nanoTime());
        playerCharacterRepository.save(new PlayerCharacter(target, "Zeta Warrior"));
        playerCharacterRepository.save(new PlayerCharacter(target, "Alpha Ranger"));

        mockMvc.perform(get("/api/admin/users/" + target.getId() + "/characters").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].character_name").value(contains("Alpha Ranger", "Zeta Warrior")))
                .andExpect(jsonPath("$[0].person_id").value(target.getId()));
    }

    @Test
    void registersACharacterForAUser() throws Exception {
        MockHttpSession admin = adminSession();
        Person target = user("addtarget-" + System.nanoTime());

        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/characters")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCharacterRequest("Given Character"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.character_name").value("Given Character"))
                .andExpect(jsonPath("$.person_id").value(target.getId()));

        PlayerCharacter created = playerCharacterRepository.findByCharacterName("Given Character").orElseThrow();
        assertThat(created.getPerson().getId()).isEqualTo(target.getId());
    }

    @Test
    void registeringACharacterBackfillsPastRunParticipants() throws Exception {
        MockHttpSession admin = adminSession();
        Person target = user("backfilltarget-" + System.nanoTime());

        GameMap map = gameMapRepository.getReferenceById(UNDERWORLD_MAP_ID);
        Run run = runRepository.save(new Run(map, Instant.now(), 1000L, Instant.now(), "victory", true, 5000L, 8));
        Profession warrior = professionRepository.findById(1).orElseThrow();
        RunParticipant participant = runParticipantRepository.save(
                new RunParticipant(run, null, "Legacy Toon", warrior, null, "T1", 0, true, false, false, 0, null));

        String body = mockMvc.perform(post("/api/admin/users/" + target.getId() + "/characters")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCharacterRequest("Legacy Toon"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long characterId = objectMapper.readTree(body).get("id").asLong();

        Long backfilled = jdbcTemplate.queryForObject(
                "SELECT character_id FROM run_participants WHERE id = ?", Long.class, participant.getId());
        assertThat(backfilled).isEqualTo(characterId);
    }

    @Test
    void rejectsRegisteringANameThatBelongsToAnotherUser() throws Exception {
        MockHttpSession admin = adminSession();
        Person owner = user("owner-" + System.nanoTime());
        Person target = user("wants-it-" + System.nanoTime());
        playerCharacterRepository.save(new PlayerCharacter(owner, "Contested Name"));

        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/characters")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCharacterRequest("Contested Name"))))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsABlankCharacterName() throws Exception {
        MockHttpSession admin = adminSession();
        Person target = user("blanktarget-" + System.nanoTime());

        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/characters")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCharacterRequest("   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404ForAnUnknownUser() throws Exception {
        MockHttpSession admin = adminSession();

        mockMvc.perform(get("/api/admin/users/99999999/characters").session(admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/users/99999999/characters")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCharacterRequest("Whatever"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void promotesAndDemotesAUser() throws Exception {
        MockHttpSession admin = adminSession();
        String targetName = "promote-" + System.nanoTime();
        MockHttpSession targetSession = signup(targetName, "password123");
        long targetId = personRepository.findByUsername(targetName).orElseThrow().getId();

        // target can't reach admin endpoints yet
        mockMvc.perform(get("/api/admin/users").session(targetSession)).andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/users/" + targetId + "/admin")
                        .session(admin).contentType(MediaType.APPLICATION_JSON).content("{\"is_admin\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_admin").value(true));
        // idempotent
        mockMvc.perform(patch("/api/admin/users/" + targetId + "/admin")
                        .session(admin).contentType(MediaType.APPLICATION_JSON).content("{\"is_admin\":true}"))
                .andExpect(status().isOk());

        // now they can (interceptor checks the DB per request — same session)
        mockMvc.perform(get("/api/admin/users").session(targetSession)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/" + targetId + "/admin")
                        .session(admin).contentType(MediaType.APPLICATION_JSON).content("{\"is_admin\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_admin").value(false));
        mockMvc.perform(get("/api/admin/users").session(targetSession)).andExpect(status().isForbidden());
    }

    @Test
    void anAdminCannotRevokeTheirOwnAccess() throws Exception {
        String username = "self-" + System.nanoTime();
        MockHttpSession session = signup(username, "password123");
        long id = personRepository.findByUsername(username).orElseThrow().getId();
        makeAdmin(id);

        mockMvc.perform(patch("/api/admin/users/" + id + "/admin")
                        .session(session).contentType(MediaType.APPLICATION_JSON).content("{\"is_admin\":false}"))
                .andExpect(status().isConflict());
        // still an admin
        mockMvc.perform(get("/api/admin/users").session(session)).andExpect(status().isOk());
    }

    @Test
    void setAdminForUnknownUserIs404AndNonAdminIsForbidden() throws Exception {
        MockHttpSession admin = adminSession();
        mockMvc.perform(patch("/api/admin/users/99999999/admin")
                        .session(admin).contentType(MediaType.APPLICATION_JSON).content("{\"is_admin\":true}"))
                .andExpect(status().isNotFound());

        MockHttpSession plain = signup("plain-admin-toggle-" + System.nanoTime(), "password123");
        Person target = user("t-" + System.nanoTime());
        mockMvc.perform(patch("/api/admin/users/" + target.getId() + "/admin")
                        .session(plain).contentType(MediaType.APPLICATION_JSON).content("{\"is_admin\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletesAUserAndCascadesEverythingDependentOnThem() throws Exception {
        MockHttpSession admin = adminSession();
        Person target = user("deleteme-" + System.nanoTime());
        long targetId = target.getId();
        target.setAlias("DeletedAlias" + System.nanoTime());
        personRepository.save(target);
        PlayerCharacter character = playerCharacterRepository.save(new PlayerCharacter(target, "Doomed Toon"));
        grantModule(targetId, seedModule("pp-doomed", false));
        makeAdmin(targetId);

        GameMap map = gameMapRepository.getReferenceById(UNDERWORLD_MAP_ID);
        Run run = runRepository.save(new Run(map, Instant.now(), 1000L, Instant.now(), "victory", true, 5000L, 8));
        Profession warrior = professionRepository.findById(1).orElseThrow();
        RunParticipant participant = runParticipantRepository.save(
                new RunParticipant(run, character, "Doomed Toon", warrior, null, "T1", 0, true, false, false, 0, null));

        mockMvc.perform(delete("/api/admin/users/" + targetId).session(admin))
                .andExpect(status().isNoContent());

        assertThat(personRepository.existsById(targetId)).isFalse();
        assertThat(playerCharacterRepository.existsById(character.getId())).isFalse();
        Integer adminRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admins WHERE person_id = ?", Integer.class, targetId);
        assertThat(adminRows).isZero();
        // The run and its participant row both survive — only the character link is cleared, same
        // as removing a single character (ON DELETE SET NULL on run_participants.character_id).
        assertThat(runRepository.existsById(run.getId())).isTrue();
        Long characterIdAfter = jdbcTemplate.queryForObject(
                "SELECT character_id FROM run_participants WHERE id = ?", Long.class, participant.getId());
        assertThat(characterIdAfter).isNull();
    }

    @Test
    void anAdminCannotDeleteTheirOwnAccount() throws Exception {
        String username = "self-delete-" + System.nanoTime();
        MockHttpSession session = signup(username, "password123");
        long id = personRepository.findByUsername(username).orElseThrow().getId();
        makeAdmin(id);

        mockMvc.perform(delete("/api/admin/users/" + id).session(session))
                .andExpect(status().isConflict());
        assertThat(personRepository.existsById(id)).isTrue();
    }

    @Test
    void deleteOfAnUnknownUserIs404AndNonAdminIsForbidden() throws Exception {
        MockHttpSession admin = adminSession();
        mockMvc.perform(delete("/api/admin/users/99999999").session(admin))
                .andExpect(status().isNotFound());

        MockHttpSession plain = signup("plain-delete-" + System.nanoTime(), "password123");
        Person target = user("delete-target-" + System.nanoTime());
        mockMvc.perform(delete("/api/admin/users/" + target.getId()).session(plain))
                .andExpect(status().isForbidden());
        assertThat(personRepository.existsById(target.getId())).isTrue();
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        MockHttpSession plain = signup("not-an-admin-" + System.nanoTime(), "password123");
        Person target = user("target-" + System.nanoTime());

        mockMvc.perform(get("/api/admin/users/" + target.getId() + "/characters").session(plain))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/characters")
                        .session(plain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCharacterRequest("Nope"))))
                .andExpect(status().isForbidden());
    }
}
