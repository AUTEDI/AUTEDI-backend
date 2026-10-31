package com.ceos24.autedi.domain.worksheet.repository;

import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WorksheetRepository extends JpaRepository<Worksheet, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Worksheet w where w.id = :id and w.academy.id = :academyId")
    Optional<Worksheet> findLockedByIdAndAcademyId(Long id, Long academyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Worksheet w where w.id = :id")
    Optional<Worksheet> findLockedById(Long id);

    @EntityGraph(attributePaths = {"academy", "user"})
    Optional<Worksheet> findByIdAndAcademyId(Long id, Long academyId);

    List<Worksheet> findAllByAcademyIdOrderByCreatedAtDesc(Long academyId);
}
