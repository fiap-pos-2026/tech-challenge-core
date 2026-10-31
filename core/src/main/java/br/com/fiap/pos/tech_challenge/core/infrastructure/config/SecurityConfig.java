package br.com.fiap.pos.tech_challenge.core.infrastructure.config;

import br.com.fiap.pos.tech_challenge.core.infrastructure.security.AppAuthEntryPoint;
import br.com.fiap.pos.tech_challenge.core.infrastructure.security.AuditLogFilter;
import br.com.fiap.pos.tech_challenge.core.infrastructure.security.AuthMode;
import br.com.fiap.pos.tech_challenge.core.infrastructure.security.CustomerJwt;
import br.com.fiap.pos.tech_challenge.core.infrastructure.security.JWTAuthorizationFilter;
import br.com.fiap.pos.tech_challenge.core.infrastructure.security.PasswordChangeRequiredFilter;
import br.com.fiap.pos.tech_challenge.core.util.Translator;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Log4j2
public class SecurityConfig {

    @Value("${security.auth.mode:BOTH}")
    private AuthMode authMode;

    @Value("${security.auth.customer.issuer:tech-challenge-auth-lambda}")
    private String customerIssuer;

    @Value("${security.auth.customer.audience:tech-challenge-core}")
    private String customerAudience;

    @Value("${security.auth.customer.jwks-uri:}")
    private String customerJwksUri;

    @Value("${security.auth.customer.public-key:}")
    private String customerPublicKeyLocation;

    private final ResourceLoader resourceLoader;

    @Value("${jwt.public.key}")
    private RSAPublicKey publicKey;

    @Value("${jwt.private.key}")
    private RSAPrivateKey privateKey;

    @Qualifier("springUserDetailsService")
    private final UserDetailsService details;

    private final AuthenticationConfiguration configuration;

    private final AppAuthEntryPoint appAuthEntryPoint;

    private final AuditLogFilter auditLogFilter;

    private final PasswordChangeRequiredFilter passwordChangeRequiredFilter;

    private final Translator translator;

    private static final String[] IGNORED_ROUTES = {
            "/webjars/**",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/signin/**",
            "/error/**",
            "/eureka/**"
    };

    private static final String[] PUBLIC_OTP_ROUTES = {
            "/api/service-orders/*/status",
            "/api/service-orders/*/approval",
            "/api/service-orders/*/delivery/accept",
            "/api/service-orders/*/delivery/reject"
    };

    // Área do cliente autenticado pelo CPF (JWT da Lambda)
    private static final String[] CUSTOMER_ROUTES = {
            "/api/customer/**"
    };

    // Login de funcionário — bloqueado só no modo LAMBDA (regressão)
    private static final String SIGNIN_ROUTE = "/api/signin/**";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        log.info("Modo de autenticação: {}", authMode);
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(it -> {
                    if (authMode == AuthMode.LAMBDA) {
                        it.requestMatchers(SIGNIN_ROUTE).denyAll();
                    }
                    it.requestMatchers(IGNORED_ROUTES).permitAll()
                            .requestMatchers(PUBLIC_OTP_ROUTES).permitAll()
                            .requestMatchers(CUSTOMER_ROUTES).hasRole(CustomerJwt.ROLE)
                            .anyRequest().access(staffOnly());
                })
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilter(authorizationFilter())
                .addFilterAfter(auditLogFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(passwordChangeRequiredFilter, BasicAuthenticationFilter.class)
                .exceptionHandling(Customizer.withDefaults())
                .oauth2ResourceServer(it -> it
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(appAuthEntryPoint))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return switch (authMode) {
            case INTERNAL -> employeeJwtDecoder();
            case LAMBDA -> requireCustomerJwtDecoder();
            case BOTH -> bothJwtDecoder();
        };
    }

    private JwtDecoder employeeJwtDecoder() {
        return NimbusJwtDecoder.withPublicKey(this.publicKey).build();
    }

    /**
     * Tenta o decoder de funcionário; se falhar e houver chave da Lambda, tenta o de cliente.
     */
    private JwtDecoder bothJwtDecoder() {
        JwtDecoder employee = employeeJwtDecoder();
        JwtDecoder customer = optionalCustomerJwtDecoder();
        if (customer == null) {
            log.warn("AUTH_MODE=BOTH sem JWKS/PEM de cliente — só JWT de funcionário será aceito até configurar CUSTOMER_JWT_*");
            return employee;
        }
        return token -> {
            try {
                return employee.decode(token);
            } catch (JwtException employeeFailure) {
                try {
                    return customer.decode(token);
                } catch (JwtException customerFailure) {
                    customerFailure.addSuppressed(employeeFailure);
                    throw customerFailure;
                }
            }
        };
    }

    private JwtDecoder requireCustomerJwtDecoder() {
        JwtDecoder decoder = optionalCustomerJwtDecoder();
        if (decoder == null) {
            throw new IllegalStateException(
                    "security.auth.mode=LAMBDA exige security.auth.customer.jwks-uri ou security.auth.customer.public-key");
        }
        return decoder;
    }

    /**
     * Decoder do JWT de cliente: chave da Lambda (JWKS ou PEM), {@code iss}, {@code aud} e role conferidos.
     * {@code null} se nenhuma chave de cliente estiver configurada.
     */
    private JwtDecoder optionalCustomerJwtDecoder() {
        NimbusJwtDecoder decoder;
        if (StringUtils.isNotBlank(customerJwksUri)) {
            decoder = NimbusJwtDecoder.withJwkSetUri(customerJwksUri).build();
        } else if (StringUtils.isNotBlank(customerPublicKeyLocation)) {
            decoder = NimbusJwtDecoder.withPublicKey(loadPublicKey(customerPublicKeyLocation)).build();
        } else {
            return null;
        }

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(customerIssuer),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(customerAudience)),
                new JwtClaimValidator<String>("role", CustomerJwt.ROLE::equals)));
        return decoder;
    }

    private RSAPublicKey loadPublicKey(String location) {
        try (var in = resourceLoader.getResource(location).getInputStream()) {
            return RsaKeyConverters.x509().convert(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler a chave pública do JWT de cliente: " + location, e);
        }
    }

    /**
     * Cliente ({@code role=CUSTOMER}) → {@link CustomerJwt}; demais → conversor padrão
     * (o {@link JWTAuthorizationFilter} troca pela autenticação do {@code User}).
     */
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        var staffConverter = new JwtAuthenticationConverter();
        return switch (authMode) {
            case INTERNAL -> staffConverter;
            case LAMBDA -> CustomerJwt::authentication;
            case BOTH -> jwt -> CustomerJwt.ROLE.equals(jwt.getClaimAsString("role"))
                    ? CustomerJwt.authentication(jwt)
                    : staffConverter.convert(jwt);
        };
    }

    /**
     * Rotas de funcionário: autenticado e não-cliente. Evita que o token de cliente alcance endpoints
     * que não têm {@code @PreAuthorize} (perfil, signout, etc.).
     */
    private static AuthorizationManager<RequestAuthorizationContext> staffOnly() {
        return (authentication, context) -> {
            var auth = authentication.get();
            return new AuthorizationDecision(auth != null
                    && auth.isAuthenticated()
                    && !(auth instanceof AnonymousAuthenticationToken)
                    && !CustomerJwt.isCustomer(auth));
        };
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        var rsaKey = new RSAKey.Builder(this.publicKey).privateKey(this.privateKey).build();
        var jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JWTAuthorizationFilter authorizationFilter() {
        return new JWTAuthorizationFilter(configuration.getAuthenticationManager(), details, translator);
    }
}
