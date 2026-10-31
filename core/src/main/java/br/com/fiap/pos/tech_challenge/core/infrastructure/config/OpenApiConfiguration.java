package br.com.fiap.pos.tech_challenge.core.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {

    public static final String SECURITY_SCHEME_NAME = "Bearer Token Authorization";

    @Bean
    public OpenAPI customOpenAPI(
            @Value("${openapi.service.title}") String serviceTitle,
            @Value("${openapi.service.description}") String serviceDesc,
            @Value("${openapi.service.version}") String serviceVersion,
            @Value("${openapi.server.url:}") String serverUrl) {
        var openApi = new OpenAPI()
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        SECURITY_SCHEME_NAME,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("AUTH_MODE=INTERNAL: token do POST /api/signin. "
                                                        + "AUTH_MODE=LAMBDA: token do cliente emitido pela Lambda (POST /auth no API Gateway).")))
                .security(List.of(new SecurityRequirement().addList(SECURITY_SCHEME_NAME)))
                .info(new Info().title(serviceTitle).description(serviceDesc).version(serviceVersion))
                .tags(List.of(
                        new Tag().name("Authentication").description("APIs related to user authentication"),
                        new Tag().name("Users").description("APIs related to user management"),
                        new Tag().name("Customer area").description("Authenticated customer (CPF token from the auth Lambda)")
                ));

        // Ex.: URL do API Gateway + /core. Sem valor, o Swagger usa o host em que foi aberto.
        if (!serverUrl.isBlank()) {
            openApi.servers(List.of(new Server().url(serverUrl).description("API Gateway")));
        }
        return openApi;
    }
}
