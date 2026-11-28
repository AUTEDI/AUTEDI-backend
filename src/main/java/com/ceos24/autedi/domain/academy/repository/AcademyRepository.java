package com.ceos24.autedi.domain.academy.repository;

import com.ceos24.autedi.domain.academy.entity.Academy;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AcademyRepository extends JpaRepository<Academy, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Academy a where a.id = :id")
    Optional<Academy> findLockedById(Long id);

    @Query("select a.id from Academy a where a.settingsInitialized=false order by a.id")
    List<Long> findUninitializedSettingsIds(Pageable page);
}
