package com.ceos24.autedi.domain.student.repository;

import com.ceos24.autedi.domain.student.entity.StudentMemo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StudentMemoRepository extends JpaRepository<StudentMemo, Long> {

    /** 학생의 메모를 최신순으로 조회한다. 작성자 이름을 함께 읽도록 작성자를 한 번에 가져온다. 정렬은 쿼리에 고정하므로 Pageable에는 정렬을 넣지 않는다. */
    @Query(
            value =
                    """
                    select m from StudentMemo m join fetch m.author
                    where m.student.id = :studentId
                    order by m.createdAt desc, m.id desc
                    """,
            countQuery = "select count(m) from StudentMemo m where m.student.id = :studentId")
    Page<StudentMemo> findPageByStudentId(Long studentId, Pageable pageable);
}
