package com.howl.uwtracker.repository;

import com.howl.uwtracker.domain.RunParticipantItemDrop;
import com.howl.uwtracker.domain.RunParticipantItemDropId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunParticipantItemDropRepository extends JpaRepository<RunParticipantItemDrop, RunParticipantItemDropId> {

    /** Clears a participant's previously recorded drops before re-inserting on a resend — see UploadRunWriter. */
    void deleteById_RunParticipantId(Long runParticipantId);
}
