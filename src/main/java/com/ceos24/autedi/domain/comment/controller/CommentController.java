package com.ceos24.autedi.domain.comment.controller;

import com.ceos24.autedi.domain.comment.dto.request.CommentCreateRequest;
import com.ceos24.autedi.domain.comment.dto.request.CommentUpdateRequest;
import com.ceos24.autedi.domain.comment.dto.response.CommentListResponse;
import com.ceos24.autedi.domain.comment.dto.response.CommentResponse;
import com.ceos24.autedi.domain.comment.service.CommentService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "07. Teacher Comment", description = "선생님 코멘트 및 강조 영역 작성/조회/수정/삭제(F06) API")
@RestController
@RequestMapping("/api/v1/worksheets/{worksheetId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(
            summary = "교사 코멘트 목록 조회 (F06)",
            description = "저장된 교사 코멘트와 하이라이트/밑줄 영역을 조회합니다. pageId 파라미터로 페이지별 필터 조회가 가능합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<CommentListResponse>> getComments(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId,
            @Parameter(description = "페이지 ID 필터", required = false) @RequestParam(value = "pageId", required = false)
                    Long pageId) {
        CommentListResponse response = commentService.getComments(principal.academyId(), worksheetId, pageId);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @Operation(
            summary = "교사 코멘트 단건 상세 조회 (F06)",
            description = "코멘트 한 건의 내용, 선택 영역 좌표, 강조 방식(HIGHLIGHT / UNDERLINE)을 조회합니다.")
    @GetMapping("/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> getComment(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId,
            @Parameter(description = "코멘트 고유 ID", required = true) @PathVariable("commentId") Long commentId) {
        CommentResponse response = commentService.getComment(principal.academyId(), worksheetId, commentId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(ApiResponse.onSuccess(response));
    }

    @Operation(summary = "교사 코멘트 작성 (F06)", description = "선택한 원문 영역에 하이라이트 또는 밑줄과 함께 교사 코멘트를 등록합니다. (201 Created)")
    @PostMapping
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId,
            @Valid @RequestBody CommentCreateRequest request) {
        CommentResponse response =
                commentService.createComment(principal.userId(), principal.academyId(), worksheetId, request);
        URI location = URI.create("/api/v1/worksheets/" + worksheetId + "/comments/" + response.id());
        return ResponseEntity.created(location)
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess("코멘트가 작성되었습니다.", response));
    }

    @Operation(
            summary = "교사 코멘트 내용 수정 (F06)",
            description = "코멘트 텍스트 내용을 수정합니다. 좌표 영역은 불변이며 content만 변경됩니다. ETag 동시성 제어를 지원합니다.")
    @PatchMapping("/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId,
            @Parameter(description = "코멘트 고유 ID", required = true) @PathVariable("commentId") Long commentId,
            @Parameter(description = "동시성 제어를 위한 최신 ETag (If-Match: \\\"com_1-v1\\\")", required = false)
                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false)
                    String ifMatch,
            @Valid @RequestBody CommentUpdateRequest request) {
        CommentResponse response =
                commentService.updateComment(principal.academyId(), worksheetId, commentId, ifMatch, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess("코멘트 내용이 수정되었습니다.", response));
    }

    @Operation(
            summary = "교사 코멘트 삭제 (F06)",
            description = "코멘트와 연결된 강조 표시를 함께 소프트 삭제합니다. 원본과 AI 첨삭은 영향받지 않습니다. (204 No Content)")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "학습지 고유 ID", required = true) @PathVariable("worksheetId") Long worksheetId,
            @Parameter(description = "코멘트 고유 ID", required = true) @PathVariable("commentId") Long commentId,
            @Parameter(description = "동시성 제어를 위한 최신 ETag", required = false)
                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false)
                    String ifMatch) {
        commentService.deleteComment(principal.academyId(), worksheetId, commentId, ifMatch);
        return ResponseEntity.noContent().build();
    }
}
