package com.ceos24.autedi.global.config;

import com.ceos24.autedi.domain.generation.contract.GenerationContractProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GenerationContractProperties.class)
public class GenerationContractConfiguration {}
