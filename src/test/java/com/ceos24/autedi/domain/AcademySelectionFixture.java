package com.ceos24.autedi.domain;

import com.ceos24.autedi.domain.academy.service.AcademySettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** 기존 이름/연락처/중복 등록 회귀 테스트의 표시값을 미리 설정한 선택지 ID로 변환한다. */
final class AcademySelectionFixture {
    private final AcademySettingsService settings;
    private final Long academyId;
    private final ObjectMapper json = new ObjectMapper();

    AcademySelectionFixture(AcademySettingsService settings, Long academyId) {
        this.settings = settings;
        this.academyId = academyId;
    }

    Long grade(String name) {
        var state = settings.get(academyId);
        var grade = state.grades().stream()
                .filter(g -> g.name().equals(name.strip()))
                .findFirst()
                .orElseGet(() -> settings.createGrade(academyId, name.strip()));
        if (!grade.enabled()) {
            var ids = new ArrayList<>(state.grades().stream()
                    .filter(g -> g.enabled())
                    .map(g -> g.gradeId())
                    .toList());
            ids.add(grade.gradeId());
            settings.selectGrades(academyId, ids);
        }
        return grade.gradeId();
    }

    Long schoolClass(String name) {
        if (name == null || name.isBlank()) return null;
        return settings.get(academyId).classes().stream()
                .filter(c -> c.name().equals(name.strip()))
                .findFirst()
                .orElseGet(() -> settings.createClass(academyId, name.strip()))
                .classId();
    }

    String request(Object input) throws Exception {
        if (input instanceof String raw) {
            try {
                input = json.readValue(raw, Object.class);
            } catch (Exception ignored) {
                return raw;
            }
        }
        if (!(input instanceof Map<?, ?> source)) return json.writeValueAsString(input);
        var body = new LinkedHashMap<Object, Object>(source);
        if (body.get("grade") instanceof String name
                && !name.isBlank()
                && name.strip().length() <= 50) {
            body.remove("grade");
            body.put("gradeId", grade(name));
        }
        if (body.containsKey("className")
                && (body.get("className") == null
                        || body.get("className") instanceof String name
                                && name.strip().length() <= 100)) {
            Object name = body.remove("className");
            body.put("classId", schoolClass((String) name));
        }
        return json.writeValueAsString(body);
    }
}
