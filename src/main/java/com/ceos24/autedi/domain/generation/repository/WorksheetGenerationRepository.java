package com.ceos24.autedi.domain.generation.repository;

import com.ceos24.autedi.domain.generation.entity.GenerationStatus;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WorksheetGenerationRepository extends JpaRepository<WorksheetGeneration, Long> {

    Optional<WorksheetGeneration> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from WorksheetGeneration g where g.id = :id")
    Optional<WorksheetGeneration> findLockedById(Long id);

    @Query("select g.worksheet.id from WorksheetGeneration g where g.id = :id")
    Optional<Long> findWorksheetIdById(Long id);

    @Query("select g.id from WorksheetGeneration g where g.status = :status and g.clientMode is not null order by g.id")
    List<Long> findBackgroundIds(GenerationStatus status, Pageable pageable);

    @Query(
            "select g.id from WorksheetGeneration g where g.status = com.ceos24.autedi.domain.generation.entity.GenerationStatus.RUNNING or (g.status = com.ceos24.autedi.domain.generation.entity.GenerationStatus.PENDING and g.clientMode is null)")
    List<Long> findInterruptedIds();

    @EntityGraph(attributePaths = {"worksheet", "requestedBy"})
    Optional<WorksheetGeneration> findFirstByWorksheetIdOrderByCreatedAtDescIdDesc(Long worksheetId);
}
