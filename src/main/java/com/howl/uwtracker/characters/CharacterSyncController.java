package com.howl.uwtracker.characters;

import com.howl.uwtracker.characters.dto.SyncCharactersResponse;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.web.MachineKeyAuthenticationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code POST /sync-characters} — GW Launcher Reforged calls this with its locally-detected
 * character list when a user has sync enabled and a machine key configured. Top-level, not under
 * {@code /api/**}, per the plugin/launcher-facing convention (specs/backend/00-overview.md's
 * "Routing" section) — same posture as {@code /module-entitlements}.
 *
 * <p>Never reassigns a name already registered (by this person or anyone else); see
 * {@link CharacterService#syncFromLauncher}. No plugin-version gate and no
 * {@code can_report_failures}-style permission check — same bar as {@code /upload-run}'s own
 * auto-claim of the uploader's character: a valid, unrevoked machine key is enough.
 */
@RestController
public class CharacterSyncController {

    private final MachineKeyAuthenticationService machineKeyAuthenticationService;
    private final CharacterService characterService;

    public CharacterSyncController(MachineKeyAuthenticationService machineKeyAuthenticationService,
                                    CharacterService characterService) {
        this.machineKeyAuthenticationService = machineKeyAuthenticationService;
        this.characterService = characterService;
    }

    @PostMapping(value = "/sync-characters", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SyncCharactersResponse> sync(
            @RequestHeader(value = "X-Machine-Key", required = false) String machineKey,
            @RequestBody(required = false) List<String> characterNames) {
        Person person = machineKeyAuthenticationService.authenticateWithoutVersionCheck(machineKey);
        List<String> added = characterService.syncFromLauncher(person.getId(), characterNames);
        return ResponseEntity.ok(new SyncCharactersResponse(added));
    }
}
