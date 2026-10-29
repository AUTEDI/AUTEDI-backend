package com.ceos24.autedi.domain.worksheet.repository;

import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorksheetRepository extends JpaRepository<Worksheet, Long> {

    @EntityGraph(attributePaths = {"academy", "user"})
    Optional<Worksheet> findByIdAndAcademyId(Long id, Long academyId);

    List<Worksheet> findAllByAcademyIdOrderByCreatedAtDesc(Long academyId);
}
