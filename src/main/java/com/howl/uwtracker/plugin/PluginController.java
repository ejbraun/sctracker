package com.howl.uwtracker.plugin;

import com.howl.uwtracker.auth.CurrentPersonId;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.repository.PersonRepository;
import com.howl.uwtracker.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/plugin")
public class PluginController {

    private final PersonRepository personRepository;

    public PluginController(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    /**
     * Called by the frontend's download link's onClick — fire-and-forget alongside the actual
     * browser-native file download (this endpoint doesn't serve the dll itself, just records that
     * the click happened). Powers the "new plugin version available" banner: only shown to people
     * who've downloaded at least once before, so this timestamp is what flips it off again after
     * they grab the latest build.
     */
    @PostMapping("/download")
    public ResponseEntity<Void> recordDownload(@CurrentPersonId Long personId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "not authenticated"));
        person.setLastPluginDownloadAt(Instant.now());
        personRepository.save(person);
        return ResponseEntity.ok().build();
    }
}
