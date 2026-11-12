package com.ceos24.autedi.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "QR 토큰 기반 요청 (scan, status, token 공용)")
public record QrTokenRequest(
        @Schema(description = "QR 링크의 token 값", example = "Zk3w...") @NotBlank @Size(max = 64) String qrToken) {}
