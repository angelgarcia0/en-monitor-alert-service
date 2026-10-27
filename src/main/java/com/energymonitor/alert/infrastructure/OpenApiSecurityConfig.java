package com.energymonitor.alert.infrastructure;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the OpenAPI description and the one security scheme this API authenticates with: a
 * bearer JWT signed by the security service.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Energy Monitor Alert API",
        version = "v1",
        description = "The alerts of a home: a reading above its device's power threshold, the "
                + "home's consumption limit reached, a device that lost its connection and the "
                + "first reading of a newly linked device. The bearer token is issued by the "
                + "security service."))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiSecurityConfig {
}
