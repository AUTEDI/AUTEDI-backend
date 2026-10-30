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
}
