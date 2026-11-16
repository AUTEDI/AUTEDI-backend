package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiLocationContextTest {
    @Test
    void normalizesOnlyLineTerminatorsAndPreservesWhitespaceAndEmptyLines() {
        AiLocationContext context = AiLocationContext.fromText(2, " first \r\n\r\nthird\rlast\n");
        assertEquals(2, context.pageNumber());
        assertEquals(List.of(" first ", "", "third", "last", ""), context.transcriptionLines());
    }

    @Test
    void copiesTheConfirmedSourceInsteadOfKeepingAMutableInput() {
        List<String> lines = new ArrayList<>(List.of("source", ""));
        AiLocationContext context = new AiLocationContext(1, lines);
        lines.set(0, "changed");
        assertEquals(List.of("source", ""), context.transcriptionLines());
        assertThrows(UnsupportedOperationException.class, () -> context.transcriptionLines()
                .clear());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n", "\r\n\t"})
    void rejectsSourceWithNoReadableText(String text) {
        assertThrows(IllegalArgumentException.class, () -> AiLocationContext.fromText(1, text));
    }

    @Test
    void rejectsInvalidPageAndEmbeddedLineTerminators() {
        assertThrows(IllegalArgumentException.class, () -> new AiLocationContext(0, List.of("source")));
        assertThrows(IllegalArgumentException.class, () -> new AiLocationContext(1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AiLocationContext(1, List.of("two\nlines")));
        assertThrows(IllegalArgumentException.class, () -> new AiLocationContext(1, List.of("two\rlines")));
        assertThrows(NullPointerException.class, () -> AiLocationContext.fromText(1, null));
    }
}
