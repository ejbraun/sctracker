package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JsonController {

    private static final Logger log = LoggerFactory.getLogger(JsonController.class);

    @PostMapping(value = "/upload-runs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> logJson(@RequestHeader("X-Machine-Key") String machineKey,
                                         @RequestBody String rawJson) {
        log.info("Received X-Machine-Key: {}", machineKey);
        log.info("Received JSON: {}", rawJson);
        return ResponseEntity.ok().build();
    }
}
