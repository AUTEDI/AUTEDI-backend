package com.ceos24.autedi.domain.academy.service;

import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** 배포 전부터 존재하던 학생도 설정 화면 방문 없이 ID로 조회되도록 학원 단위로 이관한다. */
@Component
@RequiredArgsConstructor
public class AcademySettingsInitializer implements ApplicationRunner {
    private final AcademyRepository academies;
    private final AcademySettingsService settings;

    @Override
    public void run(ApplicationArguments args) {
        while (true) {
            var ids = academies.findUninitializedSettingsIds(PageRequest.of(0, 100));
            if (ids.isEmpty()) return;
            ids.forEach(settings::prepare);
        }
    }
}
