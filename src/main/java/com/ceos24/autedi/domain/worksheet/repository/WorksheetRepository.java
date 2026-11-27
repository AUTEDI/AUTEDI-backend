package com.ceos24.autedi.domain.worksheet.repository;

import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WorksheetRepository extends JpaRepository<Worksheet, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Worksheet w where w.id = :id and w.academy.id = :academyId")
    Optional<Worksheet> findLockedByIdAndAcademyId(Long id, Long academyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Worksheet w where w.id = :id")
    Optional<Worksheet> findLockedById(Long id);

    @EntityGraph(attributePaths = {"academy", "user"})
    Optional<Worksheet> findByIdAndAcademyId(Long id, Long academyId);

    List<Worksheet> findAllByAcademyIdOrderByCreatedAtDesc(Long academyId);

    /** 학생 결과물은 최근 명시적 저장일 기준. 완료는 기관 공유, 임시저장은 업로더만 노출한다. */
    @Query(
            value =
                    """
            select new com.ceos24.autedi.domain.worksheet.repository.SavedWorksheetRow(
                w.id, w.title, w.saveStatus, w.status, w.lastSavedAt, w.updatedAt,
                s.id, s.name, s.grade, s.className, u.id, u.name)
            from Worksheet w join w.student s join w.user u
            where w.academy.id = :academyId and s.academy.id = :academyId and u.academy.id = :academyId
              and s.id = :studentId and w.deletedAt is null and w.lastSavedAt is not null
              and (w.saveStatus = com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus.COMPLETED
                   or (w.saveStatus = com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus.TEMP_SAVED and u.id = :userId))
              and exists (select r.worksheetId from WorksheetSavedResult r where r.worksheetId = w.id)
              and (:pattern is null or lower(w.title) like :pattern escape '!')
              and (:from is null or w.lastSavedAt >= :from)
              and (:to is null or w.lastSavedAt <= :to)
            order by w.lastSavedAt desc, w.id desc
            """,
            countQuery =
                    """
            select count(w) from Worksheet w join w.student s join w.user u
            where w.academy.id = :academyId and s.academy.id = :academyId and u.academy.id = :academyId
              and s.id = :studentId and w.deletedAt is null and w.lastSavedAt is not null
              and (w.saveStatus = com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus.COMPLETED
                   or (w.saveStatus = com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus.TEMP_SAVED and u.id = :userId))
              and exists (select r.worksheetId from WorksheetSavedResult r where r.worksheetId = w.id)
              and (:pattern is null or lower(w.title) like :pattern escape '!')
              and (:from is null or w.lastSavedAt >= :from)
              and (:to is null or w.lastSavedAt <= :to)
            """)
    Page<SavedWorksheetRow> searchStudentSavedResults(
            Long academyId,
            Long studentId,
            Long userId,
            String pattern,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);

    /** 홈은 추가 편집을 포함한 최근 작업 시각 기준이며, 목록 공개 범위는 학생 결과물과 동일하다. */
    @Query(
            value =
                    """
            select new com.ceos24.autedi.domain.worksheet.repository.SavedWorksheetRow(
                w.id, w.title, w.saveStatus, w.status, w.lastSavedAt, w.updatedAt,
                s.id, s.name, s.grade, s.className, u.id, u.name)
            from Worksheet w join w.student s join w.user u
            where w.academy.id = :academyId and s.academy.id = :academyId and u.academy.id = :academyId
              and w.deletedAt is null and w.lastSavedAt is not null
              and (w.saveStatus = com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus.COMPLETED
                   or (w.saveStatus = com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus.TEMP_SAVED and u.id = :userId))
              and exists (select r.worksheetId from WorksheetSavedResult r where r.worksheetId = w.id)
            order by w.updatedAt desc, w.id desc
            """,
            countQuery =
                    """
            select count(w) from Worksheet w join w.student s join w.user u
            where w.academy.id = :academyId and s.academy.id = :academyId and u.academy.id = :academyId
              and w.deletedAt is null and w.lastSavedAt is not null
              and (w.saveStatus = com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus.COMPLETED
                   or (w.saveStatus = com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus.TEMP_SAVED and u.id = :userId))
              and exists (select r.worksheetId from WorksheetSavedResult r where r.worksheetId = w.id)
            """)
    Page<SavedWorksheetRow> searchRecentSavedResults(Long academyId, Long userId, Pageable pageable);

    /**
     * 선생님별 첨삭 수. 업로더가 올린 학습지 중 {@code statuses} 상태이고 삭제되지 않았으며 처음 저장한 시각이 {@code from} 이상 {@code to}
     * 미만인 건수다. 건수가 없는 선생님은 결과에 나오지 않는다.
     */
    @Query(
            """
            select new com.ceos24.autedi.domain.worksheet.repository.TeacherWorksheetCount(w.user.id, count(w))
            from Worksheet w
            where w.academy.id = :academyId
              and w.user.id in :teacherIds
              and w.saveStatus in :statuses
              and w.deletedAt is null
              and w.firstSavedAt >= :from
              and w.firstSavedAt < :to
            group by w.user.id
            """)
    List<TeacherWorksheetCount> countByTeacher(
            Long academyId,
            List<Long> teacherIds,
            List<WorksheetSaveStatus> statuses,
            LocalDateTime from,
            LocalDateTime to);

    /**
     * 한 선생님의 첨삭 이력. {@link #countByTeacher}와 같은 조건이며, 검색어({@code pattern})는 제목 또는 학생 이름과 일치하는 것만 고른다.
     * {@code pattern}이 null이면 검색하지 않는다. 처음 저장한 시각 최신순이고 같으면 ID가 큰 순이다.
     */
    @Query(
            value =
                    """
                    select w from Worksheet w left join fetch w.student s
                    where w.academy.id = :academyId
                      and w.user.id = :teacherId
                      and w.saveStatus in :statuses
                      and w.deletedAt is null
                      and w.firstSavedAt >= :from
                      and w.firstSavedAt < :to
                      and (:pattern is null
                           or lower(w.title) like :pattern escape '!'
                           or lower(s.name) like :pattern escape '!')
                    order by w.firstSavedAt desc, w.id desc
                    """,
            countQuery =
                    """
                    select count(w) from Worksheet w left join w.student s
                    where w.academy.id = :academyId
                      and w.user.id = :teacherId
                      and w.saveStatus in :statuses
                      and w.deletedAt is null
                      and w.firstSavedAt >= :from
                      and w.firstSavedAt < :to
                      and (:pattern is null
                           or lower(w.title) like :pattern escape '!'
                           or lower(s.name) like :pattern escape '!')
                    """)
    Page<Worksheet> searchTeacherHistory(
            Long academyId,
            Long teacherId,
            List<WorksheetSaveStatus> statuses,
            LocalDateTime from,
            LocalDateTime to,
            String pattern,
            Pageable pageable);
}
