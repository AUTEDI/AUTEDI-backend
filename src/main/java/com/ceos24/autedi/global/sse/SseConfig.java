package com.ceos24.autedi.global.sse;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SseProperties.class)
public class SseConfig {}
