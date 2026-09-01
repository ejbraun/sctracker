package com.howl.uwtracker.ingestion;

import com.howl.uwtracker.domain.MapConfig;
import com.howl.uwtracker.domain.MapConfigId;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.RoleModel;
import com.howl.uwtracker.ingestion.dto.PartyDto;
import com.howl.uwtracker.ingestion.dto.PartyMemberDto;
import com.howl.uwtracker.ingestion.dto.UploadRunRequest;
import com.howl.uwtracker.ingestion.dto.UploadRunResponse;
import com.howl.uwtracker.repository.MapConfigRepository;
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

    private final MachineKeyAuthenticationService machineKeyAuthenticationService;
    private final MapConfigRepository mapConfigRepository;
    private final PlayerCharacterRepository playerCharacterRepository;
    private final MapDedupLock mapDedupLock;
    private final UploadRunWriter writer;

    public UploadRunService(MachineKeyAuthenticationService machineKeyAuthenticationService, MapConfigRepository mapConfigRepository,
                             PlayerCharacterRepository playerCharacterRepository, MapDedupLock mapDedupLock, UploadRunWriter writer) {
        this.machineKeyAuthenticationService = machineKeyAuthenticationService;
        this.mapConfigRepository = mapConfigRepository;
        this.playerCharacterRepository = playerCharacterRepository;
        this.mapDedupLock = mapDedupLock;
        this.writer = writer;
    }

    /**
     * The registered-character floor for a party of {@code partySize} — 50% of the roster rounded
     * down, but never below 1 (Underworld 8-man → 4, Fissure of Woe duo → 1, a FoW solo run → 1:
     * whoever ran it must be a registered character, otherwise the run attributes to nobody). Keeps
     * pug/scrub groups out while still allowing unregistered slots (a guildmate who just hasn't
     * registered yet). The admin retroactive-wipe cleanup applies the same bar per run (see
     * RunRepository.findIdsWithFewerThanHalfPartyRegistered). See specs/features/fow-and-party-size.md.
     */
    public static int minRegisteredFor(int partySize) {
        return Math.max(1, partySize / 2);
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
        if (size == 0) {
            log.warn("rejecting upload: no party members (personId={}, mapId={})", uploader.getId(), party.mapId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "party members are required");
        }

        // Supported (map, party_size) combinations are a curated set seeded by migration
        // (map_configs) — not auto-discovered from whatever an upload happens to carry. This one
        // lookup covers both "unknown map id" (no configs at all) and "wrong roster size for this
        // map". The matched row also carries the role model used below.
        MapConfig config = mapConfigRepository.findById(new MapConfigId(party.mapId(), size))
                .orElseThrow(() -> {
                    log.warn("rejecting upload: unsupported map/party-size combination {}/{} (personId={})",
                            party.mapId(), size, uploader.getId());
                    return new ApiException(HttpStatus.BAD_REQUEST,
                            "unsupported map/party-size combination: " + party.mapId() + "/" + size);
                });

        // A "registered character" is one with a characters row (someone's claimed it via
        // POST /api/characters) — same lookup UploadRunWriter uses to link a participant to an
        // account. Requiring a size-scaled minimum keeps out pug/scrub groups; unregistered slots
        // are still allowed, just not a majority of the party.
        int minRegistered = minRegisteredFor(size);
        long registeredCount = members.stream()
                .filter(m -> playerCharacterRepository.existsByCharacterName(m.name()))
                .count();
        if (registeredCount < minRegistered) {
            log.warn("rejecting upload: only {} of {} party members are registered characters (personId={}, mapId={})",
                    registeredCount, size, uploader.getId(), party.mapId());
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "at least " + minRegistered + " party members must be registered characters");
        }

        if (request.objective() == null) {
            log.warn("rejecting upload: missing objective section (personId={}, mapId={})",
                    uploader.getId(), party.mapId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "objective is required");
        }

        // Only the uploader's own character can have a trustworthy role_hint (see RoleDerivation's
        // class doc) — strip any hint value on every other entry before deriving roles from it. The
        // role model comes from this run's (map, party_size) config.
        RoleModel roleModel = config.getRoleModel();
        List<String> roles = RoleDerivation.resolveRoles(
                RoleDerivation.restrictHintsToSelf(party.characterName(), members), roleModel);

        return mapDedupLock.withLock(party.mapId(),
                () -> writer.ingest(party, members, roles, request.objective(), uploader.getId(), roleModel));
    }
}
