package com.ceos24.autedi.domain.academy.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;

public final class AcademySettingsRequest {
    private AcademySettingsRequest() {}

    public record GradeSelection(@NotNull @Size(max = 200) List<@NotNull @Positive Long> gradeIds) {}

    public record GradeCreate(@NotBlank @Size(max = 50) String name) {
        public GradeCreate {
            name = name == null ? null : name.strip();
        }
    }

    public record ClassName(@NotBlank @Size(max = 100) String name) {
        public ClassName {
            name = name == null ? null : name.strip();
        }
    }
}
