package com.ceos24.autedi.domain.academy.repository;

import com.ceos24.autedi.domain.academy.entity.AcademyGrade;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademyGradeRepository extends JpaRepository<AcademyGrade, Long> {
    List<AcademyGrade> findByAcademyIdOrderByIdAsc(Long academyId);

    Optional<AcademyGrade> findByIdAndAcademyId(Long id, Long academyId);

    Optional<AcademyGrade> findByAcademyIdAndName(Long academyId, String name);

    void deleteByAcademyId(Long academyId);
}
