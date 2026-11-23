package com.ceos24.autedi.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

class DevDataInitializerLogTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(DevDataInitializer.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    @DisplayName("시드 계정 로그에는 이메일·이름이 없고 역할로 계정을 구분한다")
    void seedLogsHaveRoleInsteadOfEmail() {
        AcademyRepository academyRepository = mock(AcademyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(academyRepository.save(any(Academy.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new DevDataInitializer(academyRepository, userRepository, passwordEncoder).run();

        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).anySatisfy(message -> assertThat(message)
                .contains("role=DIRECTOR"));
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).anySatisfy(message -> assertThat(message)
                .contains("role=TEACHER"));
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).noneSatisfy(message -> assertThat(message)
                .containsAnyOf("@", "autedi.com", "에이플러스", "대표원장", "담당선생님"));
    }
}
