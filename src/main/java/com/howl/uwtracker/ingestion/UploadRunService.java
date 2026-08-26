package com.howl.uwtracker.ingestion;

import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.ingestion.dto.PartyDto;
import com.howl.uwtracker.ingestion.dto.PartyMemberDto;
import com.howl.uwtracker.ingestion.dto.UploadRunRequest;
import com.howl.uwtracker.ingestion.dto.UploadRunResponse;
import com.howl.uwtracker.repository.GameMapRepository;
import com.howl.uwtracker.repository.PlayerCharacterRepository;
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

    // Below this, a run is more likely a pug/scrub/pickup group than a guild run worth tracking —
    // see the rejection check in processUpload for the actual rationale. Public: AdminRunService
    // reuses this as the single source of truth when retroactively wiping pre-existing runs that
    // wouldn't have cleared this same bar had it existed at upload time.
    public static final int MIN_REGISTERED_CHARACTERS = 4;

    private final MachineKeyAuthenticationService machineKeyAuthenticationService;
    private final GameMapRepository gameMapRepository;
    private final PlayerCharacterRepository playerCharacterRepository;
    private final MapDedupLock mapDedupLock;
    private final UploadRunWriter writer;

    public UploadRunService(MachineKeyAuthenticationService machineKeyAuthenticationService, GameMapRepository gameMapRepository,
                             PlayerCharacterRepository playerCharacterRepository, MapDedupLock mapDedupLock, UploadRunWriter writer) {
        this.machineKeyAuthenticationService = machineKeyAuthenticationService;
        this.gameMapRepository = gameMapRepository;
        this.playerCharacterRepository = playerCharacterRepository;
        this.mapDedupLock = mapDedupLock;
        this.writer = writer;
    }

    // pluginVersion (X-Plugin-Version) is enforced inside authenticate() itself — an outdated
    // client gets a 426 Upgrade Required there (see PluginVersionMetadataLoader) rather than the
    // old silent-drop-and-pretend-success behavior; the plugin now has a real, distinct signal to
    // react to instead of the upload just vanishing.
    public UploadRunResponse processUpload(String rawMachineKey, Integer pluginVersion, UploadRunRequest request) {
        Person uploader = machineKeyAuthenticationService.authenticateForUpload(rawMachineKey, pluginVersion);

        PartyDto party = request.party();
        List<PartyMemberDto> members = party.partyMembers();
        int size = members == null ? 0 : members.size();
        if (size != 8) {
            log.warn("rejecting upload: party size {} != 8 (personId={}, mapId={})",
                    size, uploader.getId(), party.mapId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "party size must be 8");
        }

        // A "registered character" is one with a characters row (someone's claimed it via
        // POST /api/characters) — same lookup UploadRunWriter uses to link a participant to an
        // account. Requiring a minimum keeps out pug/scrub groups with few or no guild members in
        // them; unregistered slots are still allowed (heroes/henchmen, or a guildmate who just
        // hasn't registered yet), just not a majority of the party.
        long registeredCount = members.stream()
                .filter(m -> playerCharacterRepository.existsByCharacterName(m.name()))
                .count();
        if (registeredCount < MIN_REGISTERED_CHARACTERS) {
            log.warn("rejecting upload: only {} of {} party members are registered characters (personId={}, mapId={})",
                    registeredCount, size, uploader.getId(), party.mapId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "at least " + MIN_REGISTERED_CHARACTERS + " party members must be registered characters");
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
