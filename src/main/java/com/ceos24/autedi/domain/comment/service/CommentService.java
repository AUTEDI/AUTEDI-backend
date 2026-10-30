package com.ceos24.autedi.domain.comment.service;

import com.ceos24.autedi.domain.comment.dto.request.CommentCreateRequest;
import com.ceos24.autedi.domain.comment.dto.request.CommentUpdateRequest;
import com.ceos24.autedi.domain.comment.dto.response.CommentListResponse;
import com.ceos24.autedi.domain.comment.dto.response.CommentResponse;
import com.ceos24.autedi.domain.comment.entity.Comment;
import com.ceos24.autedi.domain.comment.repository.CommentRepository;
import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final WorksheetRepository worksheetRepository;
    private final WorksheetPageRepository worksheetPageRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    public CommentListResponse getComments(Long academyId, Long worksheetId, Long pageId) {
        findWorksheetAndValidateReady(academyId, worksheetId);

        List<CommentResponse> items = commentRepository.findAllActiveByWorksheet(worksheetId, pageId).stream()
                .map(CommentResponse::from)
                .toList();

        return CommentListResponse.of(items);
    }

    public CommentResponse getComment(Long academyId, Long worksheetId, Long commentId) {
        findWorksheetAndValidateReady(academyId, worksheetId);

        Comment comment = findActiveCommentByIdAndWorksheetId(commentId, worksheetId);
        return CommentResponse.from(comment);
    }

    @Transactional
    public CommentResponse createComment(Long userId, Long academyId, Long worksheetId, CommentCreateRequest request) {
        Worksheet worksheet = findWorksheetAndValidateReady(academyId, worksheetId);

        WorksheetPage page = worksheetPageRepository
                .findByIdAndWorksheetId(request.pageId(), worksheetId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAGE_NOT_FOUND));

        if (request.regions() == null || request.regions().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "강조 영역을 최소 한 개 이상 지정해 주세요.");
        }

        for (RegionPayload region : request.regions()) {
            if (region.pageId() != null && !region.pageId().equals(page.getId())) {
                throw new CustomException(ErrorCode.COMMENT_PAGE_MISMATCH);
            }
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Comment comment = Comment.builder()
                .worksheet(worksheet)
                .page(page)
                .user(user)
                .highlightType(request.highlightType())
                .regions(request.regions())
                .content(request.content().trim())
                .build();
        commentRepository.save(comment);

        log.info("교사 코멘트 작성 완료: commentId={}, worksheetId={}, pageId={}", comment.getId(), worksheetId, page.getId());

        return CommentResponse.from(comment);
    }

    @Transactional
    public CommentResponse updateComment(
            Long academyId, Long worksheetId, Long commentId, String ifMatch, CommentUpdateRequest request) {
        findWorksheetAndValidateReady(academyId, worksheetId);

        Comment comment = findActiveCommentByIdAndWorksheetId(commentId, worksheetId);

        validateIfMatch(ifMatch, comment.getEtag());

        comment.updateContent(request.content().trim());
        log.info("교사 코멘트 내용 수정 완료: commentId={}, worksheetId={}", commentId, worksheetId);

        String nextEtag = "\"com_" + comment.getId() + "-v" + (comment.getVersion() + 1) + "\"";
        return CommentResponse.of(comment, nextEtag);
    }

    @Transactional
    public void deleteComment(Long academyId, Long worksheetId, Long commentId, String ifMatch) {
        findWorksheetAndValidateReady(academyId, worksheetId);

        Comment comment = findActiveCommentByIdAndWorksheetId(commentId, worksheetId);

        validateIfMatch(ifMatch, comment.getEtag());

        comment.delete();
        log.info("교사 코멘트 삭제 완료: commentId={}, worksheetId={}", commentId, worksheetId);
    }

    private Worksheet findWorksheetAndValidateReady(Long academyId, Long worksheetId) {
        Worksheet worksheet = worksheetRepository
                .findByIdAndAcademyId(worksheetId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));

        if (worksheet.getStatus() != WorksheetStatus.READY) {
            throw new CustomException(ErrorCode.INVALID_WORKSHEET_STATE, "학습지가 검토 준비 완료(READY) 상태가 아닙니다.");
        }
        return worksheet;
    }

    private Comment findActiveCommentByIdAndWorksheetId(Long commentId, Long worksheetId) {
        Comment comment = commentRepository
                .findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

        if (comment.isDeleted() || !comment.getWorksheet().getId().equals(worksheetId)) {
            throw new CustomException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return comment;
    }

    private void validateIfMatch(String rawIfMatch, String currentEtag) {
        if (rawIfMatch == null || rawIfMatch.isBlank()) {
            throw new CustomException(ErrorCode.PRECONDITION_FAILED);
        }
        String cleanIfMatch = rawIfMatch.trim().replace("\"", "");
        String cleanCurrent = currentEtag.trim().replace("\"", "");
        if (!cleanIfMatch.equals(cleanCurrent)) {
            throw new CustomException(ErrorCode.PRECONDITION_FAILED);
        }
    }
}
