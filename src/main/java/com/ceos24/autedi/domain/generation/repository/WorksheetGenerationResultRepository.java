package com.ceos24.autedi.domain.generation.repository;

import com.ceos24.autedi.domain.generation.entity.WorksheetGenerationResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

public interface WorksheetGenerationResultRepository extends Repository<WorksheetGenerationResult, Long> {
    WorksheetGenerationResult save(WorksheetGenerationResult result);

    @EntityGraph(attributePaths = {"generation", "transcription", "transcription.generation", "transcription.page"})
    Optional<WorksheetGenerationResult> findByIdAndGenerationWorksheetAcademyId(Long id, Long academyId);

    Optional<WorksheetGenerationResult> findByGenerationIdAndPageId(Long generationId, Long pageId);

    @EntityGraph(attributePaths = {"generation", "transcription", "transcription.generation", "transcription.page"})
    List<WorksheetGenerationResult> findAllByGenerationIdInOrderByPagePageNumberAsc(List<Long> generationIds);
}
