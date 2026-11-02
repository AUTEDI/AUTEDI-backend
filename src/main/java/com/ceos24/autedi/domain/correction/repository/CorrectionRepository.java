package com.ceos24.autedi.domain.correction.repository;

import com.ceos24.autedi.domain.correction.entity.Correction;
import com.ceos24.autedi.domain.correction.entity.CorrectionStatus;
import com.ceos24.autedi.domain.correction.entity.FeedbackType;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CorrectionRepository extends JpaRepository<Correction, Long> {

    @EntityGraph(attributePaths = {"worksheet", "page", "generation"})
    Optional<Correction> findById(Long id);

    @Query("SELECT c FROM Correction c WHERE c.worksheet.id = :worksheetId "
            + "AND (:status IS NULL OR c.status = :status) "
            + "AND (:pageId IS NULL OR c.page.id = :pageId) "
            + "AND (:area IS NULL OR c.area = :area) "
            + "AND (:feedbackType IS NULL OR c.feedbackType = :feedbackType) "
            + "AND (:isApplied IS NULL OR c.isApplied = :isApplied) "
            + "ORDER BY c.page.pageNumber ASC, c.id ASC")
    List<Correction> findAllByFilter(
            @Param("worksheetId") Long worksheetId,
            @Param("status") CorrectionStatus status,
            @Param("pageId") Long pageId,
            @Param("area") CorrectionArea area,
            @Param("feedbackType") FeedbackType feedbackType,
            @Param("isApplied") Boolean isApplied);

    default List<Correction> findAllByFilter(
            Long worksheetId, CorrectionStatus status, Long pageId, CorrectionArea area, FeedbackType feedbackType) {
        return findAllByFilter(worksheetId, status, pageId, area, feedbackType, null);
    }

    List<Correction> findAllByWorksheetIdAndStatus(Long worksheetId, CorrectionStatus status);
}
