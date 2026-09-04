package com.howl.uwtracker.modules;

import com.howl.uwtracker.AbstractIntegrationTest;
import com.howl.uwtracker.domain.Module;
import com.howl.uwtracker.domain.ModuleType;
import com.howl.uwtracker.domain.Person;
import com.howl.uwtracker.domain.PersonModuleGrant;
import com.howl.uwtracker.domain.PersonModuleGrantId;
import com.howl.uwtracker.repository.ModuleRepository;
import com.howl.uwtracker.repository.PersonModuleGrantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PR 1 adds no HTTP surface — this proves the new {@code modules} / {@code person_module_grants}
 * mappings validate against a real MySQL ({@code ddl-auto=validate}) and the repositories round-trip.
 */
class ModuleSchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ModuleRepository moduleRepository;

    @Autowired
    PersonModuleGrantRepository grantRepository;

    @Test
    void savesAndQueriesModules() {
        moduleRepository.save(new Module("pp-vanquish", "Vanquish aggregation", false,
                "plugins/pp-vanquish", "pp-vanquish.dll", "plugins/pp-vanquish/pp-vanquish.version.json",
                null, 5));
        moduleRepository.save(new Module("pp-public", "Public thing", true,
                "launcher", "thing.dll", null, "application/octet-stream", 1));

        List<Module> enabled = moduleRepository.findByEnabledTrueOrderBySortOrderAscModuleKeyAsc();
        assertThat(enabled).extracting(Module::getModuleKey).containsExactly("pp-public", "pp-vanquish");

        assertThat(moduleRepository.findByModuleKey("pp-vanquish")).get().satisfies(m -> {
            assertThat(m.isPublicAccess()).isFalse();
            assertThat(m.isEnabled()).isTrue();
            assertThat(m.getContentType()).isEqualTo("application/octet-stream");
            assertThat(m.getSortOrder()).isEqualTo(5);
            assertThat(m.getCreatedAt()).isNotNull();
            assertThat(m.getCurrentVersion()).isNull();
            assertThat(m.getType()).isEqualTo(ModuleType.PLUGIN); // column default
            assertThat(m.artifactPath()).isEqualTo("plugins/pp-vanquish/pp-vanquish.dll");
        });
        assertThat(moduleRepository.existsByModuleKey("nope")).isFalse();

        Module launcher = moduleRepository.save(new Module("gwrl-base", "Launcher component", true,
                "launcher/gwrl-base", "gwrl-base.exe", null, null, 0));
        launcher.setType(ModuleType.MODULE);
        moduleRepository.save(launcher);
        assertThat(moduleRepository.findByModuleKey("gwrl-base")).get()
                .extracting(Module::getType).isEqualTo(ModuleType.MODULE);
    }

    @Test
    void grantsRoundTripAndCascadeOnModuleDelete() {
        Person person = personRepository.save(new Person("grantee", "hash"));
        long moduleId = seedModule("pp-gated", false);

        grantRepository.save(new PersonModuleGrant(new PersonModuleGrantId(person.getId(), moduleId), person.getId()));

        assertThat(grantRepository.existsByIdPersonIdAndIdModuleId(person.getId(), moduleId)).isTrue();
        assertThat(grantRepository.findModuleIdsByPersonId(person.getId())).containsExactly(moduleId);
        assertThat(grantRepository.findByIdPersonId(person.getId())).singleElement().satisfies(g -> {
            assertThat(g.getGrantedAt()).isNotNull();
            assertThat(g.getGrantedBy()).isEqualTo(person.getId());
        });

        grantRepository.deleteByIdPersonIdAndIdModuleId(person.getId(), moduleId);
        assertThat(grantRepository.existsByIdPersonIdAndIdModuleId(person.getId(), moduleId)).isFalse();

        // FK ON DELETE CASCADE: dropping the module clears any lingering grants.
        grantRepository.save(new PersonModuleGrant(new PersonModuleGrantId(person.getId(), moduleId), null));
        jdbcTemplate.update("DELETE FROM modules WHERE id = ?", moduleId);
        assertThat(grantRepository.findByIdPersonId(person.getId())).isEmpty();
    }
}
