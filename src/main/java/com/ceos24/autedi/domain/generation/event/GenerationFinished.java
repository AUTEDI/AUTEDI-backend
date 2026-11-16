package com.ceos24.autedi.domain.generation.event;

/**
 * 첨삭 작업이 종료(성공/실패)되었을 때 발행된다. 실패 시에만 errorCode/retryable이 의미를 가지며 메시지 원문은 담지 않는다.
 */
public record GenerationFinished(
        Long userId, Long worksheetId, Long generationId, boolean succeeded, String errorCode, boolean retryable) {

    public static GenerationFinished success(Long userId, Long worksheetId, Long generationId) {
        return new GenerationFinished(userId, worksheetId, generationId, true, null, false);
    }

    public static GenerationFinished failure(
            Long userId, Long worksheetId, Long generationId, String errorCode, boolean retryable) {
        return new GenerationFinished(userId, worksheetId, generationId, false, errorCode, retryable);
    }
}
