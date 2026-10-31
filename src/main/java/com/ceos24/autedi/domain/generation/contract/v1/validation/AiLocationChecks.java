package com.ceos24.autedi.domain.generation.contract.v1.validation;

import java.util.List;

/** 기존 TerraCorrectionExample의 findOccurrence/parseBox 알고리즘을 객체 입력에 재사용한다. 실험 원본은 보존한다. */
public final class AiLocationChecks {
    private AiLocationChecks() {}

    public static int findOccurrence(String line, String original, int occurrence) {
        if (line == null
                || original == null
                || original.isEmpty()
                || occurrence < 1
                || occurrence > line.length()
                || original.contains("\n")
                || original.contains("\r")) return -1;
        int start = -1, from = 0;
        for (int i = 0; i < occurrence; i++) {
            start = line.indexOf(original, from);
            if (start < 0) return -1;
            from = start + original.length();
        }
        return start;
    }

    /** 좌표를 뒤집거나 범위 안으로 보정하지 않는다. */
    public static List<Double> parseBox(List<Double> values) {
        if (values == null || values.size() != 4)
            throw new IllegalStateException("box requires [left, top, right, bottom]: 4 numbers");
        for (Double value : values) {
            if (value == null || !Double.isFinite(value) || value < 0 || value > 1)
                throw new IllegalStateException("box coordinates must be numbers in 0..1");
        }
        if (values.get(0) >= values.get(2) || values.get(1) >= values.get(3))
            throw new IllegalStateException("box requires left < right and top < bottom");
        return List.copyOf(values);
    }
}
