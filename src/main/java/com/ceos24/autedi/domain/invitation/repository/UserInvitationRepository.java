package com.ceos24.autedi.domain.invitation.repository;

import com.ceos24.autedi.domain.invitation.entity.InvitationStatus;
import com.ceos24.autedi.domain.invitation.entity.UserInvitation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, Long> {

    @EntityGraph(attributePaths = {"academy", "invitedBy"})
    Optional<UserInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM UserInvitation i JOIN FETCH i.academy JOIN FETCH i.invitedBy WHERE i.tokenHash = :tokenHash")
    Optional<UserInvitation> findByTokenHashWithLock(@Param("tokenHash") String tokenHash);

    List<UserInvitation> findAllByEmailAndStatus(String email, InvitationStatus status);

    /**
     * 같은 기관의 해당 상태 초대를 검색어로 걸러 가져온다. 만료 시각이 지났어도 상태가 그대로면 포함한다. 정렬과 페이지 나누기는 하지 않는다.
     *
     * @param pattern 소문자로 바꾸고 {@code !}로 이스케이프한 LIKE 패턴. 검색어가 없으면 {@code %}
     */
    @Query(
            """
            SELECT i FROM UserInvitation i
            WHERE i.academy.id = :academyId
              AND i.status = :status
              AND (LOWER(i.name) LIKE :pattern ESCAPE '!' OR LOWER(i.email) LIKE :pattern ESCAPE '!')
            """)
    List<UserInvitation> search(
            @Param("academyId") Long academyId,
            @Param("status") InvitationStatus status,
            @Param("pattern") String pattern);
}
