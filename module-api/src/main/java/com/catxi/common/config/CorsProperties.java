package com.catxi.common.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "catxi.cors")
public class CorsProperties {

  private List<String> allowedOrigins = List.of();

  public List<String> getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins;
  }

  public List<String> allowedOrigins() {
    return allowedOrigins.stream()
        .flatMap(origin -> Arrays.stream(origin.split(",")))
        .map(String::trim)
        .filter(origin -> !origin.isBlank())
        .distinct()
        .toList();
  }
}
