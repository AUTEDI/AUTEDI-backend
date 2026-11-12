package com.ceos24.autedi.domain.auth.repository;

import com.ceos24.autedi.domain.auth.entity.QrSession;
import com.ceos24.autedi.domain.auth.entity.QrSessionStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QrSessionRepository extends JpaRepository<QrSession, Long> {

    Optional<QrSession> findBySessionId(String sessionId);

    Optional<QrSession> findByQrToken(String qrToken);

    /** 휴대폰 측 전이(scan, token). 영향 행이 0이면 상태/만료 조건 불일치. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE QrSession q SET q.status = :to "
            + "WHERE q.qrToken = :qrToken AND q.status = :from AND q.expiresAt > :now")
    int transitionByQrToken(
            @Param("qrToken") String qrToken,
            @Param("from") QrSessionStatus from,
            @Param("to") QrSessionStatus to,
            @Param("now") LocalDateTime now);

    /** 이미 로그인된 폰의 link 전이. 사용자 조건을 쿼리에 포함해 다른 사용자의 연결을 원자적으로 막는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE QrSession q SET q.status = :to "
            + "WHERE q.qrToken = :qrToken AND q.userId = :userId AND q.status = :from AND q.expiresAt > :now")
    int transitionByQrTokenAndUser(
            @Param("qrToken") String qrToken,
            @Param("userId") Long userId,
            @Param("from") QrSessionStatus from,
            @Param("to") QrSessionStatus to,
            @Param("now") LocalDateTime now);

    /** 노트북 측 전이(approve, reject). 소유자 조건을 쿼리에 포함한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE QrSession q SET q.status = :to "
            + "WHERE q.sessionId = :sessionId AND q.userId = :userId "
            + "AND q.status IN :from AND q.expiresAt > :now")
    int transitionBySessionId(
            @Param("sessionId") String sessionId,
            @Param("userId") Long userId,
            @Param("from") Collection<QrSessionStatus> from,
            @Param("to") QrSessionStatus to,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE QrSession q SET q.status = com.ceos24.autedi.domain.auth.entity.QrSessionStatus.EXPIRED "
            + "WHERE q.id = :id AND q.status IN :active AND q.expiresAt <= :now")
    int expireIfOverdue(
            @Param("id") Long id, @Param("active") Collection<QrSessionStatus> active, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM QrSession q WHERE q.expiresAt < :threshold")
    int deleteByExpiresAtBefore(@Param("threshold") LocalDateTime threshold);
}
