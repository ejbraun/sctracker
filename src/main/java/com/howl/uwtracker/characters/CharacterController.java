package com.howl.uwtracker.characters;

import com.howl.uwtracker.auth.CurrentPersonId;
import com.howl.uwtracker.characters.dto.CharacterResponse;
import com.howl.uwtracker.characters.dto.CharacterSummaryResponse;
import com.howl.uwtracker.characters.dto.CreateCharacterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** specs/backend/04-characters.md. Protected by SessionAuthInterceptor (under /api/**). */
@RestController
@RequestMapping("/api/characters")
public class CharacterController {

    private final CharacterService characterService;

    public CharacterController(CharacterService characterService) {
        this.characterService = characterService;
    }

    @GetMapping
    public ResponseEntity<List<CharacterResponse>> list(@CurrentPersonId Long personId) {
        return ResponseEntity.ok(characterService.listMine(personId));
    }

    /** Backs the Run History "character" filter dropdown — every character system-wide, not just the caller's. */
    @GetMapping("/all")
    public ResponseEntity<List<CharacterSummaryResponse>> listAll() {
        return ResponseEntity.ok(characterService.listAll());
    }

    @PostMapping
    public ResponseEntity<CharacterResponse> add(@CurrentPersonId Long personId, @RequestBody CreateCharacterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(characterService.add(personId, request.characterName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@CurrentPersonId Long personId, @PathVariable Long id) {
        characterService.remove(personId, id);
        return ResponseEntity.noContent().build();
    }
}
