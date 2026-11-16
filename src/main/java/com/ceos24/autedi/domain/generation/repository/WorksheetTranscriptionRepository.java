package com.ceos24.autedi.domain.generation.repository;

import com.ceos24.autedi.domain.generation.entity.WorksheetTranscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

/** 아카이브는 저장·조회만 제공한다. 수정·삭제 메서드는 노출하지 않는다. */
public interface WorksheetTranscriptionRepository extends Repository<WorksheetTranscription, Long> {
    WorksheetTranscription save(WorksheetTranscription transcription);

    @EntityGraph(attributePaths = {"generation", "page"})
    Optional<WorksheetTranscription> findByIdAndGenerationWorksheetAcademyId(Long id, Long academyId);

    Optional<WorksheetTranscription> findByGenerationIdAndPageId(Long generationId, Long pageId);
}
