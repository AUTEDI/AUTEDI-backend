package com.ceos24.autedi.domain.academy.repository;

import com.ceos24.autedi.domain.academy.entity.AcademyClass;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademyClassRepository extends JpaRepository<AcademyClass, Long> {
    List<AcademyClass> findByAcademyIdOrderByIdAsc(Long academyId);

    Optional<AcademyClass> findByIdAndAcademyId(Long id, Long academyId);

    Optional<AcademyClass> findByAcademyIdAndName(Long academyId, String name);

    void deleteByAcademyId(Long academyId);
}
