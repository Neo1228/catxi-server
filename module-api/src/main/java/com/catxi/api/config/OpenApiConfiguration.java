package com.catxi.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
  @Bean
  public OpenAPI openAPI() {

    Info info = new Info().title("CATXI API Document").version("1.0").description("CATXI에요");

    String jwtSchemeName = "JWT_t0ken";
    SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

    Components components =
        new Components()
            .addSecuritySchemes(
                jwtSchemeName,
                new SecurityScheme()
                    .name(jwtSchemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT"));

    // Swagger UI 설정 및 보안 추가
    return new OpenAPI()
        .addServersItem(new Server().url("/"))
        .components(components)
        .info(info)
        .addSecurityItem(securityRequirement);
  }
}
