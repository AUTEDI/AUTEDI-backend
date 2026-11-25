package com.ceos24.autedi.domain.academy.service;

import com.ceos24.autedi.domain.academy.dto.request.AcademyUpdateRequest;
import com.ceos24.autedi.domain.academy.dto.response.AcademyResponse;
import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademyService {

    private final AcademyRepository academyRepository;

    /** 기관명을 바꾼다. 앞뒤 공백은 제거해서 저장한다. */
    @Transactional
    public AcademyResponse updateName(Long academyId, AcademyUpdateRequest request) {
        Academy academy = academyRepository
                .findById(academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACADEMY_NOT_FOUND));
        academy.updateName(request.name().trim());
        return AcademyResponse.from(academy);
    }
}
