package com.ceos24.autedi.domain.user.repository;

import com.ceos24.autedi.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"academy"})
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
