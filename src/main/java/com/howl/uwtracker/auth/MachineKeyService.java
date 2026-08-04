package com.howl.uwtracker.auth;

import com.howl.uwtracker.auth.dto.GeneratedMachineKeyResponse;
import com.howl.uwtracker.domain.MachineKey;
import com.howl.uwtracker.repository.MachineKeyRepository;
import com.howl.uwtracker.repository.PersonRepository;
import com.howl.uwtracker.web.ApiException;
import com.howl.uwtracker.web.MachineKeyHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MachineKeyService {

    private final MachineKeyRepository machineKeyRepository;
    private final PersonRepository personRepository;

    public MachineKeyService(MachineKeyRepository machineKeyRepository, PersonRepository personRepository) {
        this.machineKeyRepository = machineKeyRepository;
        this.personRepository = personRepository;
    }

    public GeneratedMachineKeyResponse generate(Long personId, String label) {
        String rawKey = MachineKeyHasher.generateRawKey();
        MachineKey key = new MachineKey(personRepository.getReferenceById(personId), MachineKeyHasher.hash(rawKey), label);
        key = machineKeyRepository.save(key);
        return new GeneratedMachineKeyResponse(key.getId(), rawKey, key.getLabel());
    }

    public List<MachineKey> list(Long personId) {
        return machineKeyRepository.findByPerson_IdOrderByCreatedAtDesc(personId);
    }

    @Transactional
    public void revoke(Long personId, Long keyId) {
        MachineKey key = machineKeyRepository.findById(keyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "machine key not found"));
        if (!key.getPerson().getId().equals(personId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not your machine key");
        }
        key.revoke();
        machineKeyRepository.save(key);
    }
}
