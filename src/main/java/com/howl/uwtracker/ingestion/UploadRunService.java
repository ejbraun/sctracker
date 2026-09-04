package com.howl.uwtracker.ingestion;

import com.howl.uwtracker.characters.CharacterService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UploadRunService {

    private static final Logger log = LoggerFactory.getLogger(UploadRunService.class);

    // GWCA's MapID::Domain_of_Anguish. The plugin's own 8-real-player gate is DoA's pug filter
    // (see 051-seed-domain-of-anguish.xml) — unlike UW/FoW there's no guild-registration signal to
    // require, so the registered-character floor below is skipped entirely for this map.
    private static final int DOMAIN_OF_ANGUISH_MAP_ID = 474;

    private final MachineKeyAuthenticationService machineKeyAuthenticationService;
    private final MapConfigRepository mapConfigRepository;
    private final PlayerCharacterRepository playerCharacterRepository;
    private final CharacterService characterService;
    private final MapDedupLock mapDedupLock;
    private final UploadRunWriter writer;

    public UploadRunService(MachineKeyAuthenticationService machineKeyAuthenticationService, MapConfigRepository mapConfigRepository,
                             PlayerCharacterRepository playerCharacterRepository, CharacterService characterService,
                             MapDedupLock mapDedupLock, UploadRunWriter writer) {
        this.machineKeyAuthenticationService = machineKeyAuthenticationService;
        this.mapConfigRepository = mapConfigRepository;
        this.playerCharacterRepository = playerCharacterRepository;
        this.characterService = characterService;
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

        // Auto-claim the uploader's own character (party.character_name) the first time we see it,
        // so a new guild member's runs count without a separate website registration step. Only
        // their own slot, only when that name is actually in the party and unclaimed — never
        // reassigns a name already on another account. Runs before the floor check below so the
        // freshly-claimed character counts toward it. A machine key already belongs to a real guild
        // member, so their own slot always counting is intended, not a hole in the pug filter.
        maybeClaimUploaderCharacter(uploader, party, members);

        // A "registered character" is one with a characters row (claimed via POST /api/characters or
        // auto-claimed just above) — same lookup UploadRunWriter uses to link a participant to an
        // account. Requiring a size-scaled minimum keeps out pug/scrub groups; unregistered slots
        // are still allowed, just not a majority of the party. Domain of Anguish is exempt — see
        // DOMAIN_OF_ANGUISH_MAP_ID above.
        if (party.mapId() != DOMAIN_OF_ANGUISH_MAP_ID) {
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

    private void maybeClaimUploaderCharacter(Person uploader, PartyDto party, List<PartyMemberDto> members) {
        String name = party.characterName();
        if (name == null || name.isBlank()) {
            return;
        }
        boolean inParty = members.stream().anyMatch(m -> name.equals(m.name()));
        if (!inParty || playerCharacterRepository.existsByCharacterName(name)) {
            return;
        }
        try {
            if (characterService.claimIfUnregistered(uploader.getId(), name)) {
                log.info("auto-claimed uploader character '{}' for personId={} on upload (mapId={})",
                        name, uploader.getId(), party.mapId());
            }
        } catch (DataIntegrityViolationException e) {
            // A concurrent upload claimed the same name first — it's registered now either way, so
            // the floor check below and UploadRunWriter's character link will both still see it.
            log.debug("lost a race auto-claiming character '{}' (personId={}) — already registered",
                    name, uploader.getId());
        }
    }
}
