package com.ceos24.autedi.domain.worksheet.service;

import com.ceos24.autedi.domain.student.dto.request.StudentWorksheetSearchRequest;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.worksheet.dto.request.RecentWorksheetSearchRequest;
import com.ceos24.autedi.domain.worksheet.dto.response.SavedWorksheetListResponse;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorksheetListService {
    private final StudentRepository students;
    private final WorksheetRepository worksheets;

    public SavedWorksheetListResponse getRecent(Long userId, Long academyId, RecentWorksheetSearchRequest request) {
        return SavedWorksheetListResponse.from(
                worksheets.searchRecentSavedResults(academyId, userId, page(request.page(), request.size())), userId);
    }

    public SavedWorksheetListResponse getStudentResults(
            Long userId, Long academyId, Long studentId, StudentWorksheetSearchRequest request) {
        var pageable = page(request.page(), request.size());
        validateDate(request.from());
        validateDate(request.to());
        if (request.from() != null && request.to() != null && request.from().isAfter(request.to())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "시작일은 종료일보다 늦을 수 없습니다.");
        }
        students.findByIdAndAcademyId(studentId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
        return SavedWorksheetListResponse.from(
                worksheets.searchStudentSavedResults(
                        academyId,
                        studentId,
                        userId,
                        pattern(request.keyword()),
                        request.from() == null ? null : request.from().atStartOfDay(),
                        request.to() == null ? null : request.to().atTime(LocalTime.of(23, 59, 59, 999_999_000)),
                        pageable),
                userId);
    }

    private PageRequest page(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "페이지는 0부터, 크기는 1~100이며 조회 범위를 초과할 수 없습니다.");
        }
        return PageRequest.of(page, size);
    }

    private void validateDate(LocalDate date) {
        if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "날짜는 YYYY-MM-DD 형식이어야 합니다.");
        }
    }

    private String pattern(String keyword) {
        return keyword == null
                ? null
                : "%"
                        + keyword.toLowerCase(Locale.ROOT)
                                .replace("!", "!!")
                                .replace("%", "!%")
                                .replace("_", "!_") + "%";
    }
}
