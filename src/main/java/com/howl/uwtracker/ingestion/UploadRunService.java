package com.howl.uwtracker.ingestion;

import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.ingestion.dto.PartyDto;
import com.howl.uwtracker.ingestion.dto.PartyMemberDto;
import com.howl.uwtracker.ingestion.dto.UploadRunRequest;
import com.howl.uwtracker.ingestion.dto.UploadRunResponse;
import com.howl.uwtracker.repository.GameMapRepository;
import com.howl.uwtracker.web.ApiException;
import com.howl.uwtracker.web.MachineKeyAuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UploadRunService {

    private static final Logger log = LoggerFactory.getLogger(UploadRunService.class);

    private final MachineKeyAuthenticationService machineKeyAuthenticationService;
    private final GameMapRepository gameMapRepository;
    private final MapDedupLock mapDedupLock;
    private final UploadRunWriter writer;

    public UploadRunService(MachineKeyAuthenticationService machineKeyAuthenticationService, GameMapRepository gameMapRepository,
                             MapDedupLock mapDedupLock, UploadRunWriter writer) {
        this.machineKeyAuthenticationService = machineKeyAuthenticationService;
        this.gameMapRepository = gameMapRepository;
        this.mapDedupLock = mapDedupLock;
        this.writer = writer;
    }

    // pluginVersion (X-Plugin-Version) is enforced inside authenticate() itself — an outdated
    // client gets a 426 Upgrade Required there (see PluginVersionMetadataLoader) rather than the
    // old silent-drop-and-pretend-success behavior; the plugin now has a real, distinct signal to
    // react to instead of the upload just vanishing.
    public UploadRunResponse processUpload(String rawMachineKey, Integer pluginVersion, UploadRunRequest request) {
        Person uploader = machineKeyAuthenticationService.authenticate(rawMachineKey, pluginVersion);

        PartyDto party = request.party();
        List<PartyMemberDto> members = party.partyMembers();
        int size = members == null ? 0 : members.size();
        if (size != 8) {
            log.warn("rejecting upload: party size {} != 8 (personId={}, mapId={})",
                    size, uploader.getId(), party.mapId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "party size must be 8");
        }
        if (request.objective() == null) {
            log.warn("rejecting upload: missing objective section (personId={}, mapId={})",
                    uploader.getId(), party.mapId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "objective is required");
        }

        // maps is a curated set, seeded by migration (specs/backend/01) — not auto-discovered from
        // whatever map_id an upload happens to carry.
        if (!gameMapRepository.existsById(party.mapId())) {
            log.warn("rejecting upload: unsupported map id {} (personId={})", party.mapId(), uploader.getId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported map id " + party.mapId());
        }

        // Only the uploader's own character can have a trustworthy role_hint (see RoleDerivation's
        // class doc) — strip any hint value on every other entry before deriving roles from it.
        List<String> roles = RoleDerivation.resolveRoles(RoleDerivation.restrictHintsToSelf(party.characterName(), members));

        return mapDedupLock.withLock(party.mapId(),
                () -> writer.ingest(party, members, roles, request.objective(), uploader.getId()));
    }
}
