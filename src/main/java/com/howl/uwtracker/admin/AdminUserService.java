package com.howl.uwtracker.admin;

import com.howl.uwtracker.admin.dto.AdminUserResponse;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.repository.AdminRepository;
import com.howl.uwtracker.repository.PersonRepository;
import com.howl.uwtracker.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.Comparator;

@Service
public class AdminUserService {

    private final PersonRepository personRepository;
    private final AdminRepository adminRepository;

    public AdminUserService(PersonRepository personRepository, AdminRepository adminRepository) {
        this.personRepository = personRepository;
        this.adminRepository = adminRepository;
    }

    public List<AdminUserResponse> list() {
        Set<Long> adminPersonIds = adminRepository.findAllPersonIds();
        return personRepository.findAll().stream()
                .sorted(Comparator.comparing(Person::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(person -> AdminUserResponse.from(person, adminPersonIds.contains(person.getId())))
                .toList();
    }

    public AdminUserResponse setCanReportFailures(Long personId, boolean canReportFailures) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user not found"));
        person.setCanReportFailures(canReportFailures);
        personRepository.save(person);
        return AdminUserResponse.from(person, adminRepository.existsById(personId));
    }
}
