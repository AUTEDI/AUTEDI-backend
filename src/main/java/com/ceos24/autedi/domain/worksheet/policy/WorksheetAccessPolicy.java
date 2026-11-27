package com.ceos24.autedi.domain.worksheet.policy;

import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 학습지를 찾을 때 삭제·완료 상태를 한곳에서 검사한다. 서비스는 학습지를 직접 조회하지 않고 이 클래스를 거친다.
 *
 * <ul>
 *   <li>삭제된 학습지는 존재하지 않는 것처럼 WORKSHEET_NOT_FOUND(404)로 숨긴다.
 *   <li>완료된 학습지는 읽을 수 있지만 쓰기에서는 WORKSHEET_COMPLETED(409)로 거부한다.
 *   <li>삭제 검사가 완료 검사보다 먼저이므로, 삭제된 완료 학습지는 404다.
 * </ul>
 *
 * 트랜잭션은 호출하는 서비스가 연다. {@link #getForWrite}는 비관적 락을 쓰므로 반드시 트랜잭션 안에서 호출한다.
 */
@Component
@RequiredArgsConstructor
public class WorksheetAccessPolicy {

    private final WorksheetRepository worksheetRepository;

    /** 조회용. 다른 기관 학습지와 삭제된 학습지는 404다. 완료 여부는 보지 않는다. */
    public Worksheet getReadable(Long academyId, Long worksheetId) {
        return requireVisible(worksheetRepository.findByIdAndAcademyId(worksheetId, academyId));
    }

    /**
     * 쓰기용. 행을 잠근 채 삭제·완료 여부를 검사한다. 잠그지 않으면 검사 직후 완료·삭제가 끼어들어 완료된 학습지가 수정될 수 있다. 삭제는
     * 404, 완료는 409다.
     */
    public Worksheet getForWrite(Long academyId, Long worksheetId) {
        Worksheet worksheet = requireVisible(worksheetRepository.findLockedByIdAndAcademyId(worksheetId, academyId));
        if (worksheet.isCompleted()) {
            throw new CustomException(ErrorCode.WORKSHEET_COMPLETED);
        }
        return worksheet;
    }

    private Worksheet requireVisible(Optional<Worksheet> found) {
        return found.filter(worksheet -> !worksheet.isDeleted())
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));
    }
}
