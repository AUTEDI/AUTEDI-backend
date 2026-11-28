package com.ceos24.autedi.domain.student.service;

import com.ceos24.autedi.domain.correction.entity.FeedbackTaxonomy;
import com.ceos24.autedi.domain.student.dto.request.GrowthReportRequest;
import com.ceos24.autedi.domain.student.dto.response.GrowthReportResponse;
import com.ceos24.autedi.domain.student.dto.response.GrowthReportResponse.*;
import com.ceos24.autedi.domain.student.repository.GrowthTranscriptionRow;
import com.ceos24.autedi.domain.student.repository.StudentGrowthRepository;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSavedResult;
import com.ceos24.autedi.domain.worksheet.service.WorksheetSnapshotCodec;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentGrowthService {
    private final StudentRepository students;
    private final StudentGrowthRepository growth;

    public GrowthReportResponse getReport(Long academyId, Long studentId, GrowthReportRequest request) {
        students.findByIdAndAcademyId(studentId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate from = request.from(today);
        var saved = growth.findCompleted(
                academyId,
                studentId,
                from == null ? null : from.atStartOfDay(),
                today.plusDays(1).atStartOfDay());
        var generationIds = saved.stream()
                .filter(r -> r.getSourceGeneration() != null)
                .map(r -> r.getSourceGeneration().getId())
                .distinct()
                .toList();
        List<GrowthTranscriptionRow> transcripts = new ArrayList<>();
        for (int i = 0; i < generationIds.size(); i += 500)
            transcripts.addAll(growth.findTranscriptions(
                    academyId, generationIds.subList(i, Math.min(i + 500, generationIds.size()))));
        var byGeneration = transcripts.stream().collect(Collectors.groupingBy(GrowthTranscriptionRow::generationId));
        List<Point> points = new ArrayList<>();
        for (var row : saved) {
            try {
                var snapshot = WorksheetSnapshotCodec.decode(row);
                var review = snapshot.review();
                var w = row.getWorksheet();
                if (review.worksheet() == null
                        || !Objects.equals(review.worksheet().id(), w.getId())
                        || review.worksheet().saveStatus() != WorksheetSaveStatus.COMPLETED
                        || !Objects.equals(review.worksheet().completedAt(), w.getCompletedAt())
                        || review.worksheet().student() == null
                        || !Objects.equals(review.worksheet().student().studentId(), studentId)
                        || !Objects.equals(w.getFileHash(), row.getSourceFileHash()))
                    throw new IllegalArgumentException("최종 저장 메타데이터 불일치");
                Long generationId = row.getSourceGeneration() == null
                        ? null
                        : row.getSourceGeneration().getId();
                points.add(GrowthReportCalculator.calculate(
                        review,
                        generationId,
                        row.getSourceFileHash(),
                        generationId == null ? List.of() : byGeneration.getOrDefault(generationId, List.of()),
                        request.area()));
            } catch (CustomException | IllegalArgumentException | NullPointerException e) {
                log.warn(
                        "[growth] 저장본 집계 실패: worksheetId={}, studentId={}, reason={}",
                        row.getWorksheetId(),
                        studentId,
                        e.toString(),
                        e);
                points.add(unreadable(row));
            }
        }
        var series = FeedbackTaxonomy.AREAS.stream()
                .filter(area -> request.area() == null || request.area() == area)
                .map(area -> {
                    int count = (int) points.stream()
                            .filter(p ->
                                    p.areas().stream().anyMatch(a -> a.area() == area && a.errorsPer100Words() != null))
                            .count();
                    return new SeriesStatus(
                            area,
                            count,
                            points.isEmpty()
                                    ? DataStatus.EMPTY
                                    : count < 2 ? DataStatus.INSUFFICIENT_DATA : DataStatus.AVAILABLE);
                })
                .toList();
        int comparable = series.stream()
                .mapToInt(SeriesStatus::comparablePointCount)
                .max()
                .orElse(0);
        int unavailable = (int) points.stream()
                .filter(p -> p.aggregationStatus() == AggregationStatus.UNREADABLE_SNAPSHOT)
                .count();
        DataStatus status = points.isEmpty()
                ? DataStatus.EMPTY
                : comparable < 2 ? DataStatus.INSUFFICIENT_DATA : DataStatus.AVAILABLE;
        return new GrowthReportResponse(
                studentId,
                request.period(),
                request.area(),
                from,
                today,
                "CONFIRMED_FEEDBACK_V1",
                status,
                2,
                comparable,
                unavailable,
                List.copyOf(points),
                series);
    }

    private Point unreadable(WorksheetSavedResult saved) {
        var w = saved.getWorksheet();
        return GrowthReportCalculator.unreadable(w.getId(), w.getTitle(), w.getCompletedAt());
    }
}
