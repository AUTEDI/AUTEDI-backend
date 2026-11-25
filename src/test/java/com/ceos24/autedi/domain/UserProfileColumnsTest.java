package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UserProfileColumnsTest {

    @Autowired
    private AcademyRepository academyRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("users.phone_number, users.hire_date 컬럼이 있고, 새로 만든 사용자는 두 값이 null")
    void phoneNumberAndHireDate_columnsExist_nullByDefault() {
        Academy academy = academyRepository.save(Academy.builder()
                .name("테스트 어학원")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusMonths(3))
                .build());
        User teacher = userRepository.saveAndFlush(User.builder()
                .academy(academy)
                .email("profile_columns@autedi.com")
                .passwordHash("hashed")
                .name("김선생")
                .role(Role.TEACHER)
                .status(UserStatus.ACTIVE)
                .build());

        assertThat(teacher.getPhoneNumber()).isNull();
        assertThat(teacher.getHireDate()).isNull();

        entityManager
                .createNativeQuery("UPDATE users SET phone_number = ?1, hire_date = ?2 WHERE id = ?3")
                .setParameter(1, "010-1234-5678")
                .setParameter(2, LocalDate.of(2026, 3, 2))
                .setParameter(3, teacher.getId())
                .executeUpdate();
        entityManager.clear();

        User saved = userRepository.findById(teacher.getId()).orElseThrow();
        assertThat(saved.getPhoneNumber()).isEqualTo("010-1234-5678");
        assertThat(saved.getHireDate()).isEqualTo(LocalDate.of(2026, 3, 2));
    }
}
