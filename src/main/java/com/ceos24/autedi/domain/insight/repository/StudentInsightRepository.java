package com.ceos24.autedi.domain.insight.repository;

import com.ceos24.autedi.domain.insight.entity.StudentInsight;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StudentInsightRepository extends JpaRepository<StudentInsight, Long> {
    @Query(
            """
            select new com.ceos24.autedi.domain.insight.repository.InsightSource(
                r.worksheetId, r.revision, r.schemaVersion, r.sourceFileHash, g.id, w.completedAt)
            from WorksheetSavedResult r join r.worksheet w join w.student s join w.user u
            left join r.sourceGeneration g
            where s.id=:studentId and s.academy.id=:academyId and w.academy.id=:academyId and u.academy.id=:academyId
              and w.saveStatus=com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus.COMPLETED
              and w.deletedAt is null and w.completedAt>=:from and w.completedAt<:until
              and g.status=com.ceos24.autedi.domain.generation.entity.GenerationStatus.SUCCEEDED
              and g.clientMode=com.ceos24.autedi.domain.generation.entity.GenerationClientMode.TERRA
              and r.sourceFileHash=w.fileHash and r.sourceFileHash=g.requestFileHash
            order by w.completedAt desc, w.id desc
            """)
    List<InsightSource> findSources(
            Long academyId, Long studentId, LocalDateTime from, LocalDateTime until, Pageable page);

    @Query(
            """
            select new com.ceos24.autedi.domain.insight.repository.InsightSnapshot(r.schemaVersion, r.snapshot)
            from WorksheetSavedResult r join r.worksheet w join w.student s
            where r.worksheetId=:worksheetId and r.revision=:revision and s.id=:studentId
              and s.academy.id=:academyId and w.academy.id=:academyId and w.deletedAt is null
              and w.saveStatus=com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus.COMPLETED
            """)
    Optional<InsightSnapshot> findSnapshot(Long academyId, Long studentId, Long worksheetId, long revision);
}
