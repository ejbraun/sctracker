package com.howl.uwtracker.ingestion;

import com.howl.uwtracker.domain.MachineKey;
import com.howl.uwtracker.ingestion.dto.PartyDto;
import com.howl.uwtracker.ingestion.dto.PartyMemberDto;
import com.howl.uwtracker.ingestion.dto.UploadRunRequest;
import com.howl.uwtracker.ingestion.dto.UploadRunResponse;
import com.howl.uwtracker.repository.GameMapRepository;
import com.howl.uwtracker.repository.MachineKeyRepository;
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
    private final MapDedupLock mapDedupLock;
    private final UploadRunWriter writer;

    public UploadRunService(MachineKeyRepository machineKeyRepository, GameMapRepository gameMapRepository,
                             MapDedupLock mapDedupLock, UploadRunWriter writer) {
        this.machineKeyRepository = machineKeyRepository;
        this.gameMapRepository = gameMapRepository;
        this.mapDedupLock = mapDedupLock;
        this.writer = writer;
    }

    public UploadRunResponse processUpload(String rawMachineKey, UploadRunRequest request) {
        MachineKey machineKey = authenticate(rawMachineKey);

        PartyDto party = request.party();
        List<PartyMemberDto> members = party.partyMembers();
        int size = members == null ? 0 : members.size();
        if (size != 8) {
            log.warn("rejecting upload: party size {} != 8 (machineKeyId={}, mapId={})",
                    size, machineKey.getId(), party.mapId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "party size must be 8");
        }
        // maps is a curated set, seeded by migration (specs/backend/01) — not auto-discovered from
        // whatever map_id an upload happens to carry.
        if (!gameMapRepository.existsById(party.mapId())) {
            log.warn("rejecting upload: unsupported map id {} (machineKeyId={})", party.mapId(), machineKey.getId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported map id " + party.mapId());
        }

        List<String> roles = RoleDerivation.resolveRoles(members);

        return mapDedupLock.withLock(party.mapId(), () -> writer.ingest(party, members, roles, request.objective()));
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
