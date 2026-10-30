package com.ceos24.autedi.aiexperiments;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 초기 Terra polygon의 파싱·기하 형식 검증용 로컬 단위 테스트. 실제 이미지 정확도 검증이나 API 호출은 하지 않습니다. */
class TerraOcrExampleTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void restoresTiltedCornersWithoutChangingTheirOrder() {
        var points = TerraOcrExample.parsePolygon(json.readTree(
                "[0.1,0.2,0.4,0.1,0.5,0.5,0.2,0.6]"));
        assertEquals(List.of(
                new TerraOcrExample.Point(0.1, 0.2),
                new TerraOcrExample.Point(0.4, 0.1),
                new TerraOcrExample.Point(0.5, 0.5),
                new TerraOcrExample.Point(0.2, 0.6)), points);
    }

    @Test
    void acceptsImageBoundaryCoordinates() {
        assertEquals(4, TerraOcrExample.parsePolygon(
                json.readTree("[0,0,1,0,1,1,0,1]")).size());
    }

    @Test
    void rejectsMalformedOrDegenerateCoordinates() {
        for (String value : List.of(
                "null", "{}", "[0,0,1,0]",
                "[0,0,1,0,1,1,0,1,0]",
                "[\"0\",0,1,0,1,1,0,1]",
                "[0,null,1,0,1,1,0,1]",
                "[-0.1,0,1,0,1,1,0,1]",
                "[0,0,1.1,0,1,1,0,1]",
                "[0,0,0,0,0,0,0,0]")) {
            assertThrows(IllegalStateException.class,
                    () -> TerraOcrExample.parsePolygon(json.readTree(value)), value);
        }
    }
}
