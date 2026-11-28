package com.ceos24.autedi.domain.insight.service;

import com.ceos24.autedi.domain.insight.client.InsightClientProperties;
import com.ceos24.autedi.domain.insight.dto.InsightDocument;
import com.ceos24.autedi.domain.insight.dto.StudentInsightResponse.Status;
import com.ceos24.autedi.domain.insight.entity.StudentInsight;
import com.ceos24.autedi.domain.insight.repository.*;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.global.storage.LocalStorageService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 외부 HTTP 호출 없이 짧은 DB 트랜잭션만 담당한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentInsightStore {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    public static final int MINIMUM_RESULTS = 3, MAXIMUM_RESULTS = 10;
    private final StudentRepository students;
    private final UserRepository users;
    private final StudentInsightRepository insights;
    private final InsightClientProperties properties;

    public void requireStudent(Long academyId, Long studentId) {
        students.findByIdAndAcademyId(studentId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
    }

    public List<InsightSource> sources(Long academyId, Long studentId, LocalDate today) {
        return insights.findSources(
                academyId,
                studentId,
                today.minusMonths(3).atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                PageRequest.of(0, MAXIMUM_RESULTS));
    }

    public StudentInsight cached(Long studentId) {
        return insights.findById(studentId).orElse(null);
    }

    public InsightSnapshot snapshot(Long academyId, Long studentId, InsightSource source) {
        return insights.findSnapshot(academyId, studentId, source.worksheetId(), source.revision())
                .orElseThrow(() -> new CustomException(ErrorCode.INSIGHT_INPUT_CHANGED));
    }

    public String hash(Long academyId, Long studentId, List<InsightSource> sources) {
        return LocalStorageService.sha256(JSON.writeValueAsString(List.of(
                        academyId,
                        studentId,
                        InsightContract.POLICY_VERSION,
                        InsightContract.PROMPT_VERSION,
                        properties.model(),
                        properties.maxOutputTokens(),
                        sources))
                .getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public StudentInsight save(
            Long userId,
            Long academyId,
            Long studentId,
            LocalDate today,
            List<InsightSource> sources,
            String hash,
            InsightDocument result,
            int used,
            Status status) {
        var student = students.findLockedByIdAndAcademyId(studentId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
        if (!hash.equals(hash(academyId, studentId, sources(academyId, studentId, today))))
            throw new CustomException(ErrorCode.INSIGHT_INPUT_CHANGED);
        var row = insights.findById(studentId).orElseGet(() -> new StudentInsight(student));
        row.replace(
                hash,
                properties.model(),
                InsightContract.PROMPT_VERSION,
                status,
                sources,
                JSON.convertValue(result, new TypeReference<Map<String, Object>>() {}),
                used,
                today.minusMonths(3),
                today,
                LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.MICROS),
                users.findById(userId)
                        .filter(u -> u.getAcademy().getId().equals(academyId))
                        .orElseThrow(() -> new CustomException(ErrorCode.ACCESS_DENIED)));
        return insights.saveAndFlush(row);
    }
}
