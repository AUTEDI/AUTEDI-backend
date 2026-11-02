package com.ceos24.autedi.domain.worksheet.service;

import org.springframework.core.io.Resource;

public record WorksheetFileResource(String filename, String mediaType, String sha256, Resource resource) {}
