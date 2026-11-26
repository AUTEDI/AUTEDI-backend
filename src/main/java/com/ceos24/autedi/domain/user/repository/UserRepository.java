package com.ceos24.autedi.domain.user.repository;

import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.Collection;
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

    /**
     * 같은 기관의 해당 역할 사용자를 상태·검색어로 걸러 가져온다. 정렬과 페이지 나누기는 하지 않는다.
     *
     * @param pattern 소문자로 바꾸고 {@code !}로 이스케이프한 LIKE 패턴. 검색어가 없으면 {@code %}
     */
    @Query(
            """
            SELECT u FROM User u
            WHERE u.academy.id = :academyId
              AND u.role = :role
              AND u.status IN :statuses
              AND (LOWER(u.name) LIKE :pattern ESCAPE '!' OR LOWER(u.email) LIKE :pattern ESCAPE '!')
            """)
    List<User> search(
            @Param("academyId") Long academyId,
            @Param("role") Role role,
            @Param("statuses") Collection<UserStatus> statuses,
            @Param("pattern") String pattern);

    @Query("SELECT u.id FROM User u WHERE u.status = :status AND u.updatedAt > :since")
    List<Long> findIdsByStatusAndUpdatedAtAfter(
            @Param("status") UserStatus status, @Param("since") LocalDateTime since);
}
