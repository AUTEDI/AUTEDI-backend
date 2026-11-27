package com.ceos24.autedi.domain.student.repository;

import com.ceos24.autedi.domain.worksheet.entity.WorksheetSavedResult;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface StudentGrowthRepository extends Repository<WorksheetSavedResult, Long> {
    @Query(
            """
            select r from WorksheetSavedResult r join fetch r.worksheet w
            join w.student s join w.user u left join fetch r.sourceGeneration
            where w.academy.id = :academyId and s.academy.id = :academyId and u.academy.id = :academyId
              and s.id = :studentId and w.deletedAt is null
              and w.saveStatus = com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus.COMPLETED
              and w.completedAt is not null and (:from is null or w.completedAt >= :from)
              and w.completedAt < :until
            order by w.completedAt asc, w.id asc
            """)
    List<WorksheetSavedResult> findCompleted(Long academyId, Long studentId, LocalDateTime from, LocalDateTime until);

    @Query(
            """
            select new com.ceos24.autedi.domain.student.repository.GrowthTranscriptionRow(
                g.id, w.id, p.id, t.id, t.sourceFileHash, t.text)
            from WorksheetGenerationResult r join r.generation g join g.worksheet w
            join r.transcription t join t.page p
            where g.id in :generationIds and w.academy.id = :academyId
              and t.generation.worksheet.id = w.id and p.worksheet.id = w.id
              and t.sourceFileHash = g.requestFileHash
            """)
    List<GrowthTranscriptionRow> findTranscriptions(Long academyId, List<Long> generationIds);
}
