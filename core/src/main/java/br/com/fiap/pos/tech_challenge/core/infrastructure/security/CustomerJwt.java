package br.com.fiap.pos.tech_challenge.core.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Regras do JWT de cliente emitido pela Lambda (CPF → token).
 * O cliente não é um {@code User} do core: a autenticação fica como {@link JwtAuthenticationToken}
 * com {@code sub} = UUID do cliente e a role {@code CUSTOMER}.
 *
 * @author johncgo
 * @since 2026-10-30
 */
public final class CustomerJwt {

    public static final String ROLE = "CUSTOMER";

    public static final String AUTHORITY = "ROLE_" + ROLE;

    private CustomerJwt() {
    }

    public static JwtAuthenticationToken authentication(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(AUTHORITY)), jwt.getSubject());
    }

    public static boolean isCustomer(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken
                && authentication.getAuthorities().stream().anyMatch(a -> AUTHORITY.equals(a.getAuthority()));
    }
}
