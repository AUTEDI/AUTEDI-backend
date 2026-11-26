package com.ceos24.autedi.domain.student.repository;

import com.ceos24.autedi.domain.student.entity.Student;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {

    @EntityGraph(attributePaths = "academy")
    Optional<Student> findByIdAndAcademyId(Long id, Long academyId);
}
