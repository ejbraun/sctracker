package com.howl.uwtracker.ingestion;

import com.howl.uwtracker.domain.MachineKey;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.ingestion.dto.PartyDto;
import com.howl.uwtracker.ingestion.dto.PartyMemberDto;
import com.howl.uwtracker.ingestion.dto.UploadRunRequest;
import com.howl.uwtracker.ingestion.dto.UploadRunResponse;
import com.howl.uwtracker.plugin.PluginVersionService;
import com.howl.uwtracker.repository.GameMapRepository;
import com.howl.uwtracker.repository.MachineKeyRepository;
import com.howl.uwtracker.repository.PersonRepository;
import com.howl.uwtracker.web.ApiException;
import com.howl.uwtracker.web.MachineKeyHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UploadRunService {

    private static final Logger log = LoggerFactory.getLogger(UploadRunService.class);

    private final MachineKeyRepository machineKeyRepository;
    private final GameMapRepository gameMapRepository;
    private final PersonRepository personRepository;
    private final MapDedupLock mapDedupLock;
    private final UploadRunWriter writer;
    private final PluginVersionService pluginVersionService;

    public UploadRunService(MachineKeyRepository machineKeyRepository, GameMapRepository gameMapRepository,
                             PersonRepository personRepository, MapDedupLock mapDedupLock, UploadRunWriter writer,
                             PluginVersionService pluginVersionService) {
        this.machineKeyRepository = machineKeyRepository;
        this.gameMapRepository = gameMapRepository;
        this.personRepository = personRepository;
        this.mapDedupLock = mapDedupLock;
        this.writer = writer;
        this.pluginVersionService = pluginVersionService;
    }

    public UploadRunResponse processUpload(String rawMachineKey, UploadRunRequest request) {
        MachineKey machineKey = authenticate(rawMachineKey);
        // machineKey was loaded outside any transaction (open-in-view is disabled, and each
        // repository call auto-commits its own), so machineKey.getPerson() is an uninitialized lazy
        // proxy — fine for .getId() (Hibernate resolves that without a DB hit) but not for reading an
        // actual field. findById runs its own self-contained transaction and returns a fully-loaded
        // entity, safe to read from afterward.
        Person uploader = personRepository.findById(machineKey.getPerson().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid or revoked machine key"));

        // Silently drop uploads from an outdated plugin build rather than rejecting them: an old
        // client wouldn't know how to react to an error response anyway, and this keeps it out of
        // the data (e.g. a build missing a field that later breaks role derivation) without needing
        // the plugin itself to change. Same response shape/status as a real success — just no run_id.
        if (pluginVersionService.isOutdated(uploader)) {
            log.warn("silently dropping upload from outdated plugin build (machineKeyId={}, personId={})",
                    machineKey.getId(), uploader.getId());
            return new UploadRunResponse(null, false);
        }

        PartyDto party = request.party();
        List<PartyMemberDto> members = party.partyMembers();
        int size = members == null ? 0 : members.size();
        if (size != 8) {
            log.warn("rejecting upload: party size {} != 8 (machineKeyId={}, mapId={})",
                    size, machineKey.getId(), party.mapId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "party size must be 8");
        }
        if (request.objective() == null) {
            log.warn("rejecting upload: missing objective section (machineKeyId={}, mapId={})",
                    machineKey.getId(), party.mapId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "objective is required");
        }

        // maps is a curated set, seeded by migration (specs/backend/01) — not auto-discovered from
        // whatever map_id an upload happens to carry.
        if (!gameMapRepository.existsById(party.mapId())) {
            log.warn("rejecting upload: unsupported map id {} (machineKeyId={})", party.mapId(), machineKey.getId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported map id " + party.mapId());
        }

        List<String> roles = RoleDerivation.resolveRoles(members);

        return mapDedupLock.withLock(party.mapId(),
                () -> writer.ingest(party, members, roles, request.objective(), uploader.getId()));
    }

    private MachineKey authenticate(String rawMachineKey) {
        if (rawMachineKey == null || rawMachineKey.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "missing X-Machine-Key");
        }
        String hash = MachineKeyHasher.hash(rawMachineKey);
        return machineKeyRepository.findByKeyHashAndRevokedAtIsNull(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid or revoked machine key"));
    }
}
