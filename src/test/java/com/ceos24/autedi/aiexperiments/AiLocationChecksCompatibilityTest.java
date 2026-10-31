package com.ceos24.autedi.aiexperiments;

import static org.junit.jupiter.api.Assertions.*;

import com.ceos24.autedi.domain.generation.contract.v1.validation.AiLocationChecks;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 기존 실험 소스를 바꾸지 않고 재사용한 anchor·boxes 알고리즘의 결과를 직접 대조한다. main은 호출하지 않는다. */
class AiLocationChecksCompatibilityTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void anchorSearchMatchesExperimentIncludingUtf16AndNonOverlappingOccurrences() {
        for (String line : List.of("go then go", "😀 go", "aaaaa", "", " Go go ")) {
            for (String original : List.of("go", "Go", "aa", "", "go\nthen", "go\rthen")) {
                for (int occurrence : List.of(-1, 0, 1, 2, 3, Integer.MAX_VALUE)) {
                    assertEquals(
                            TerraCorrectionExample.findOccurrence(line, original, occurrence),
                            AiLocationChecks.findOccurrence(line, original, occurrence));
                }
            }
        }
    }

    @Test
    void normalizedBoxValidationMatchesExperimentWithoutClampingOrSwapping() {
        for (List<Double> values : List.of(
                List.of(0.0, 0.0, 1.0, 1.0), List.of(0.1, 0.2, 0.3, 0.4),
                List.of(1.0, 1.0, 1.0, 1.0), List.of(0.8, 0.1, 0.2, 0.5),
                List.of(-0.1, 0.0, 1.0, 1.0), List.of(0.0, 0.0, 1.1, 1.0),
                List.of(0.0, 0.0, 1.0), List.of(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0))) {
            var raw = json.readTree(json.writeValueAsString(values));
            try {
                List<Double> expected = TerraCorrectionExample.parseBox(raw);
                assertEquals(expected, AiLocationChecks.parseBox(values));
            } catch (IllegalStateException e) {
                IllegalStateException actual =
                        assertThrows(IllegalStateException.class, () -> AiLocationChecks.parseBox(values));
                assertEquals(e.getMessage(), actual.getMessage());
            }
        }
    }
}
