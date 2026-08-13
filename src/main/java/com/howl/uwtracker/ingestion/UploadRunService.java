package com.howl.uwtracker.ingestion;

import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.ingestion.dto.PartyDto;
import com.howl.uwtracker.ingestion.dto.PartyMemberDto;
import com.howl.uwtracker.ingestion.dto.UploadRunRequest;
import com.howl.uwtracker.ingestion.dto.UploadRunResponse;
import com.howl.uwtracker.plugin.PluginVersionService;
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
    private final PluginVersionService pluginVersionService;

    public UploadRunService(MachineKeyAuthenticationService machineKeyAuthenticationService, GameMapRepository gameMapRepository,
                             MapDedupLock mapDedupLock, UploadRunWriter writer,
                             PluginVersionService pluginVersionService) {
        this.machineKeyAuthenticationService = machineKeyAuthenticationService;
        this.gameMapRepository = gameMapRepository;
        this.mapDedupLock = mapDedupLock;
        this.writer = writer;
        this.pluginVersionService = pluginVersionService;
    }

    public UploadRunResponse processUpload(String rawMachineKey, UploadRunRequest request) {
        Person uploader = machineKeyAuthenticationService.authenticate(rawMachineKey);

        // Silently drop uploads from an outdated plugin build rather than rejecting them: an old
        // client wouldn't know how to react to an error response anyway, and this keeps it out of
        // the data (e.g. a build missing a field that later breaks role derivation) without needing
        // the plugin itself to change. Same response shape/status as a real success — just no run_id.
        if (pluginVersionService.isOutdated(uploader)) {
            log.warn("silently dropping upload from outdated plugin build (personId={})", uploader.getId());
            return new UploadRunResponse(null, false);
        }

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

        List<String> roles = RoleDerivation.resolveRoles(members);

        return mapDedupLock.withLock(party.mapId(),
                () -> writer.ingest(party, members, roles, request.objective(), uploader.getId()));
    }
}
