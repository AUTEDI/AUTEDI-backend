package com.ceos24.autedi.global.config;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"local", "dev"})
@RequiredArgsConstructor
public class DevDataInitializer implements CommandLineRunner {

    private final AcademyRepository academyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("Initializing default Academy and Director account for development...");

            Academy academy = academyRepository.save(Academy.builder()
                    .name("에이플러스 학원")
                    .subscriptionStatus(SubscriptionStatus.ACTIVE)
                    .planExpiresAt(LocalDateTime.now().plusMonths(6))
                    .build());

            User director = userRepository.save(User.builder()
                    .academy(academy)
                    .email("director@autedi.com")
                    .passwordHash(passwordEncoder.encode("Password123!"))
                    .name("대표원장")
                    .role(Role.DIRECTOR)
                    .status(UserStatus.ACTIVE)
                    .build());

            User teacher = userRepository.save(User.builder()
                    .academy(academy)
                    .email("teacher@autedi.com")
                    .passwordHash(passwordEncoder.encode("Password123!"))
                    .name("담당선생님")
                    .role(Role.TEACHER)
                    .status(UserStatus.ACTIVE)
                    .build());

            log.info("Initialized Academy: id={}, name={}", academy.getId(), academy.getName());
            log.info("Initialized Director User: id={}, email={}", director.getId(), director.getEmail());
            log.info("Initialized Teacher User: id={}, email={}", teacher.getId(), teacher.getEmail());
        }
    }
}
