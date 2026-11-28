package com.ceos24.autedi.domain.insight.service;

import com.ceos24.autedi.domain.generation.service.AiExecutionLimiter;
import com.ceos24.autedi.domain.insight.client.InsightClient;
import com.ceos24.autedi.domain.insight.dto.InsightDocument;
import com.ceos24.autedi.domain.insight.dto.StudentInsightResponse;
import com.ceos24.autedi.domain.insight.dto.StudentInsightResponse.Status;
import com.ceos24.autedi.domain.insight.entity.StudentInsight;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StudentInsightService {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final StudentInsightStore store;
    private final InsightInputBuilder inputs;
    private final InsightClient client;
    private final AiExecutionLimiter limiter;

    public StudentInsightResponse get(Long academyId, Long studentId) {
        store.requireStudent(academyId, studentId);
        var today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        var sources = store.sources(academyId, studentId, today);
        var hash = store.hash(academyId, studentId, sources);
        var cached = store.cached(studentId);
        if (sources.size() < StudentInsightStore.MINIMUM_RESULTS) return empty(studentId, sources.size(), null, today);
        if (cached == null) return unavailable(studentId, Status.NOT_GENERATED, sources.size(), null, today);
        return from(cached, hash, sources.size(), false);
    }

    public StudentInsightResponse generate(Long userId, Long academyId, Long studentId) {
        store.requireStudent(academyId, studentId);
        var today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        var sources = store.sources(academyId, studentId, today);
        var hash = store.hash(academyId, studentId, sources);
        if (sources.size() < StudentInsightStore.MINIMUM_RESULTS) return empty(studentId, sources.size(), null, today);
        var cached = store.cached(studentId);
        if (cached != null && cached.getInputHash().equals(hash)) return from(cached, hash, sources.size(), true);
        var permit = limiter.tryInsight();
        if (permit == null) throw new CustomException(ErrorCode.INSIGHT_BUSY);
        try (permit) {
            // 실행 허가 후 다시 확인하여, 직전 요청이 저장한 동일 입력은 재사용한다.
            sources = store.sources(academyId, studentId, today);
            hash = store.hash(academyId, studentId, sources);
            cached = store.cached(studentId);
            if (sources.size() < StudentInsightStore.MINIMUM_RESULTS)
                return empty(studentId, sources.size(), null, today);
            if (cached != null && cached.getInputHash().equals(hash)) return from(cached, hash, sources.size(), true);
            var input = inputs.build(academyId, studentId, sources);
            if (input.results().size() < StudentInsightStore.MINIMUM_RESULTS)
                return empty(studentId, sources.size(), input.results().size(), today);
            Status status;
            InsightDocument document;
            if (input.evidence().isEmpty()) {
                status = Status.NO_FEEDBACK;
                document = new InsightDocument(List.of(), List.of(), List.of());
            } else {
                document = InsightContract.parse(client.generate(input.json()), input.evidence());
                status = Status.READY;
            }
            var saved = store.save(
                    userId,
                    academyId,
                    studentId,
                    today,
                    sources,
                    hash,
                    document,
                    input.results().size(),
                    status);
            return from(saved, hash, sources.size(), false);
        }
    }

    private StudentInsightResponse from(StudentInsight cached, String currentHash, int candidates, boolean reused) {
        try {
            if (cached.getSchemaVersion() != 1
                    || cached.getStatus() != Status.READY && cached.getStatus() != Status.NO_FEEDBACK)
                throw new IllegalArgumentException();
            var document = JSON.convertValue(cached.getResult(), InsightDocument.class);
            if (document == null
                    || document.insights() == null
                    || document.coachingPoints() == null
                    || document.evidence() == null) throw new IllegalArgumentException();
            return new StudentInsightResponse(
                    cached.getStudentId(),
                    cached.getStatus(),
                    !cached.getInputHash().equals(currentHash),
                    reused,
                    candidates,
                    cached.getUsedResultCount(),
                    StudentInsightStore.MINIMUM_RESULTS,
                    cached.getAnalysisFrom(),
                    cached.getAnalysisTo(),
                    cached.getGeneratedAt(),
                    document.insights(),
                    document.coachingPoints(),
                    document.evidence());
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.INSIGHT_RESULT_UNREADABLE);
        }
    }

    private StudentInsightResponse empty(Long id, int count, Integer used, LocalDate today) {
        return unavailable(id, count == 0 ? Status.EMPTY : Status.INSUFFICIENT_DATA, count, used, today);
    }

    private StudentInsightResponse unavailable(Long id, Status status, int count, Integer used, LocalDate today) {
        return new StudentInsightResponse(
                id,
                status,
                false,
                false,
                count,
                used,
                StudentInsightStore.MINIMUM_RESULTS,
                today.minusMonths(3),
                today,
                null,
                List.of(),
                List.of(),
                List.of());
    }
}
