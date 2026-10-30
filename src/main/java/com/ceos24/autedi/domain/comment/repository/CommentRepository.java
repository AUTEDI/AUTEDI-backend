package com.ceos24.autedi.domain.comment.repository;

import com.ceos24.autedi.domain.comment.entity.Comment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = {"worksheet", "page", "user"})
    Optional<Comment> findById(Long id);

    @Query("SELECT c FROM Comment c WHERE c.worksheet.id = :worksheetId "
            + "AND c.deletedAt IS NULL "
            + "AND (:pageId IS NULL OR c.page.id = :pageId) "
            + "ORDER BY c.page.pageNumber ASC, c.createdAt ASC")
    List<Comment> findAllActiveByWorksheet(
            @Param("worksheetId") Long worksheetId,
            @Param("pageId") Long pageId
    );
}
