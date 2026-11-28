package com.ceos24.autedi.domain.student.repository;

import com.ceos24.autedi.domain.academy.entity.AcademyClass;
import com.ceos24.autedi.domain.academy.entity.AcademyGrade;
import com.ceos24.autedi.domain.student.entity.Student;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StudentRepository extends JpaRepository<Student, Long> {

    @Override
    @EntityGraph(attributePaths = {"gradeOption", "schoolClass"})
    Optional<Student> findById(Long id);

    @EntityGraph(attributePaths = {"academy", "gradeOption", "schoolClass"})
    Optional<Student> findByIdAndAcademyId(Long id, Long academyId);

    @EntityGraph(attributePaths = {"gradeOption", "schoolClass"})
    List<Student> findByAcademyIdAndNameOrderByIdAsc(Long academyId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.id = :id and s.academy.id = :academyId")
    Optional<Student> findLockedByIdAndAcademyId(Long id, Long academyId);

    @Query(
            """
            select s from Student s left join s.gradeOption g left join s.schoolClass c
            where s.academy.id = :academyId
              and (:keyword is null or s.name like concat('%', :keyword, '%') escape '!')
              and (:grade is null or coalesce(g.name,s.grade) = :grade)
              and (:className is null or coalesce(c.name,s.className) = :className)
              and (:gradeId is null or g.id = :gradeId)
              and (:classId is null or c.id = :classId)
              and (:unassigned = false or (c.id is null and (s.className is null or trim(s.className) = '')))
            """)
    @EntityGraph(attributePaths = {"gradeOption", "schoolClass"})
    Page<Student> findPageByFilters(
            Long academyId,
            String keyword,
            String grade,
            String className,
            Long gradeId,
            Long classId,
            boolean unassigned,
            Pageable pageable);

    boolean existsBySchoolClassId(Long classId);

    @Query("select distinct s.grade from Student s where s.academy.id=:academyId and s.gradeOption is null")
    List<String> findLegacyGrades(Long academyId);

    @Query(
            "select distinct s.className from Student s where s.academy.id=:academyId and s.schoolClass is null and s.className is not null")
    List<String> findLegacyClasses(Long academyId);

    @Modifying(flushAutomatically = true)
    @Query(
            "update Student s set s.gradeOption=:grade where s.academy.id=:academyId and s.gradeOption is null and s.grade=:name")
    int linkLegacyGrade(Long academyId, String name, AcademyGrade grade);

    @Modifying(flushAutomatically = true)
    @Query(
            "update Student s set s.schoolClass=:schoolClass where s.academy.id=:academyId and s.schoolClass is null and s.className=:name")
    int linkLegacyClass(Long academyId, String name, AcademyClass schoolClass);
}
