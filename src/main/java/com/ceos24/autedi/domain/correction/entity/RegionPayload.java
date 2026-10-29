package com.ceos24.autedi.domain.correction.entity;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegionPayload(
        Long pageId,
        Double x,
        Double y,
        Double width,
        Double height,
        Object polygon
) {
    public RegionPayload(Double x, Double y, Double width, Double height) {
        this(null, x, y, width, height, null);
    }
}
