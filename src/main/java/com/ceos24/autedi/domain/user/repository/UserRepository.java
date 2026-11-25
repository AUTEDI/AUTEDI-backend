package com.ceos24.autedi.domain.user.repository;

import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"academy"})
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndAcademyId(Long id, Long academyId);

    @Query("SELECT u.id FROM User u WHERE u.status = :status AND u.updatedAt > :since")
    List<Long> findIdsByStatusAndUpdatedAtAfter(
            @Param("status") UserStatus status, @Param("since") LocalDateTime since);
}
