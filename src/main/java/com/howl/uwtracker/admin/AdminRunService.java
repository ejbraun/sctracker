package com.howl.uwtracker.admin;

import com.howl.uwtracker.ingestion.UploadRunService;
import com.howl.uwtracker.repository.RunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Retroactively applies {@link UploadRunService}'s "at least MIN_REGISTERED_CHARACTERS registered
 * characters" upload rule to runs ingested before that rule existed. Deleting a run cascades
 * (ON DELETE CASCADE, see the changelog) to run_objectives, run_participants,
 * run_participant_item_drops, and run_failure_reasons — nothing here deletes children explicitly.
 * Hard delete, same as {@code DELETE /api/characters/{id}} — no soft-delete/undo exists in this
 * schema, so this is genuinely irreversible; {@link AdminRunController} fronts it with a count
 * endpoint precisely so the admin UI can show what's about to be deleted before it happens.
 */
@Service
public class AdminRunService {

    private static final Logger log = LoggerFactory.getLogger(AdminRunService.class);

    private final RunRepository runRepository;

    public AdminRunService(RunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Transactional(readOnly = true)
    public long countUnregisteredRuns() {
        return unregisteredRunIds().size();
    }

    @Transactional
    public long wipeUnregisteredRuns() {
        List<Long> ids = unregisteredRunIds();
        runRepository.deleteAllByIdInBatch(ids);
        log.warn("admin wiped {} run(s) with fewer than {} registered characters",
                ids.size(), UploadRunService.MIN_REGISTERED_CHARACTERS);
        return ids.size();
    }

    private List<Long> unregisteredRunIds() {
        return runRepository.findIdsWithFewerThanNRegisteredCharacters(UploadRunService.MIN_REGISTERED_CHARACTERS);
    }
}
