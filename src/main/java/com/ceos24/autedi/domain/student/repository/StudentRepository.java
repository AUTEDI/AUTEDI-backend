package com.ceos24.autedi.domain.student.repository;

import com.ceos24.autedi.domain.student.entity.Student;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StudentRepository extends JpaRepository<Student, Long> {

    @EntityGraph(attributePaths = "academy")
    Optional<Student> findByIdAndAcademyId(Long id, Long academyId);

    List<Student> findByAcademyIdAndNameOrderByIdAsc(Long academyId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.id = :id and s.academy.id = :academyId")
    Optional<Student> findLockedByIdAndAcademyId(Long id, Long academyId);

    @Query(
            """
            select s from Student s
            where s.academy.id = :academyId
              and (:keyword is null or s.name like concat('%', :keyword, '%') escape '!')
              and (:grade is null or s.grade = :grade)
              and (:className is null or s.className = :className)
              and (:unassigned = false or s.className is null or trim(s.className) = '')
            """)
    Page<Student> findPageByFilters(
            Long academyId, String keyword, String grade, String className, boolean unassigned, Pageable pageable);
}
