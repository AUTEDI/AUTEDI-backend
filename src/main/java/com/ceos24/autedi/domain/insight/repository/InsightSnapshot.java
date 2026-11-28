package com.ceos24.autedi.domain.insight.repository;

import java.util.Map;

public record InsightSnapshot(int schemaVersion, Map<String, Object> snapshot) {}
