package com.ceos24.autedi.domain.comment.controller;

import com.ceos24.autedi.domain.comment.dto.request.CommentCreateRequest;
import com.ceos24.autedi.domain.comment.dto.request.CommentUpdateRequest;
import com.ceos24.autedi.domain.comment.dto.response.CommentListResponse;
import com.ceos24.autedi.domain.comment.dto.response.CommentResponse;
import com.ceos24.autedi.domain.comment.service.CommentService;
import com.ceos24.autedi.global.response.ApiResponse;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
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

@RestController
@RequestMapping("/api/v1/worksheets/{worksheetId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<ApiResponse<CommentListResponse>> getComments(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @RequestParam(value = "pageId", required = false) Long pageId) {
        CommentListResponse response = commentService.getComments(principal.academyId(), worksheetId, pageId);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @GetMapping("/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> getComment(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @PathVariable("commentId") Long commentId) {
        CommentResponse response = commentService.getComment(principal.academyId(), worksheetId, commentId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(ApiResponse.onSuccess(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @Valid @RequestBody CommentCreateRequest request) {
        CommentResponse response =
                commentService.createComment(principal.userId(), principal.academyId(), worksheetId, request);
        URI location = URI.create("/api/v1/worksheets/" + worksheetId + "/comments/" + response.id());
        return ResponseEntity.created(location)
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess("코멘트가 작성되었습니다.", response));
    }

    @PatchMapping("/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @PathVariable("commentId") Long commentId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody CommentUpdateRequest request) {
        CommentResponse response =
                commentService.updateComment(principal.academyId(), worksheetId, commentId, ifMatch, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, response.etag())
                .body(ApiResponse.onSuccess("코멘트 내용이 수정되었습니다.", response));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("worksheetId") Long worksheetId,
            @PathVariable("commentId") Long commentId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        commentService.deleteComment(principal.academyId(), worksheetId, commentId, ifMatch);
        return ResponseEntity.noContent().build();
    }
}
