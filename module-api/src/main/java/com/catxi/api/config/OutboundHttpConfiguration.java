package com.catxi.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OutboundHttpConfiguration {
  @Bean
  public RestClient restClient(RestClient.Builder builder) {
    return builder.build();
  }
}
