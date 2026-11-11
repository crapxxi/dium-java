package com.dium.demo.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "DIUM API",
        version = "v1",
        description = "Предзаказ еды в университетах. Защищённые эндпоинты требуют заголовок Authorization: Bearer <JWT>."))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT из ответа /api/v1/auth/login (или /api/v1/auth/2fa/verify, если включена 2FA)")
public class OpenApiConfig {
}
