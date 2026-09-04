package com.howl.uwtracker.signuplinks;

import com.howl.uwtracker.admin.dto.GeneratedSignupLinkResponse;
import com.howl.uwtracker.admin.dto.SignupLinkResponse;
import com.howl.uwtracker.domain.SignupLink;
import com.howl.uwtracker.repository.PersonRepository;
import com.howl.uwtracker.repository.SignupLinkRepository;
import com.howl.uwtracker.web.ApiException;
import com.howl.uwtracker.web.MachineKeyHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin CRUD for multi-use signup links ({@code /api/admin/signup-links}). Redemption itself lives
 * in {@code AuthService.signup} (it already owns the single-use {@code SignupKey} path); this class
 * only mints, lists, and revokes. See specs/backend/03-auth.md.
 */
@Service
public class SignupLinkService {

    static final int DEFAULT_MAX_USES = 10;
    private static final int MIN_MAX_USES = 1;
    private static final int MAX_MAX_USES = 100;
    private static final int MAX_LABEL_LENGTH = 64;

    private final SignupLinkRepository signupLinkRepository;
    private final PersonRepository personRepository;

    public SignupLinkService(SignupLinkRepository signupLinkRepository, PersonRepository personRepository) {
        this.signupLinkRepository = signupLinkRepository;
        this.personRepository = personRepository;
    }

    @Transactional
    public GeneratedSignupLinkResponse create(Long adminPersonId, String label, Integer maxUses) {
        int cap = maxUses == null ? DEFAULT_MAX_USES : maxUses;
        if (cap < MIN_MAX_USES || cap > MAX_MAX_USES) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "max_uses must be between " + MIN_MAX_USES + " and " + MAX_MAX_USES);
        }
        String trimmedLabel = (label == null || label.isBlank()) ? null : label.trim();
        if (trimmedLabel != null && trimmedLabel.length() > MAX_LABEL_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "label must be at most " + MAX_LABEL_LENGTH + " characters");
        }

        String rawToken = MachineKeyHasher.generateRawKey();
        SignupLink link = signupLinkRepository.save(new SignupLink(
                MachineKeyHasher.hash(rawToken), trimmedLabel, cap, personRepository.getReferenceById(adminPersonId)));
        return new GeneratedSignupLinkResponse(link.getId(), rawToken, link.getLabel(), link.getMaxUses());
    }

    @Transactional(readOnly = true)
    public List<SignupLinkResponse> list() {
        return signupLinkRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(SignupLinkResponse::from)
                .toList();
    }

    @Transactional
    public void revoke(Long id) {
        SignupLink link = signupLinkRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "signup link not found"));
        link.revoke();
    }
}
