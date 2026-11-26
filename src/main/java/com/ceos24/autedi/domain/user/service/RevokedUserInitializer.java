package com.ceos24.autedi.domain.user.service;

import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.security.RevokedUserRegistry;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서버가 시작할 때 최근 Access Token 수명 안에 퇴사 처리된 사용자를 차단 목록에 다시 채운다. 재시작으로 인메모리 목록이 비면 방금 퇴사한 사용자의 기존 토큰이 다시
 * 통과하기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RevokedUserInitializer {

    private final UserRepository userRepository;
    private final RevokedUserRegistry revokedUserRegistry;

    @EventListener(ApplicationReadyEvent.class)
    public void reload() {
        LocalDateTime since = LocalDateTime.now().minus(revokedUserRegistry.getTtl());
        List<Long> userIds = userRepository.findIdsByStatusAndUpdatedAtAfter(UserStatus.RESIGNED, since);
        userIds.forEach(revokedUserRegistry::revoke);
        log.info("[auth] 퇴사 차단 목록 복원: count={}", userIds.size());
    }
}
