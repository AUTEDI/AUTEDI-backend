package com.ceos24.autedi.domain.generation.repository;

import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorksheetGenerationRepository extends JpaRepository<WorksheetGeneration, Long> {

    Optional<WorksheetGeneration> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"worksheet", "requestedBy"})
    Optional<WorksheetGeneration> findFirstByWorksheetIdOrderByCreatedAtDesc(Long worksheetId);
}
