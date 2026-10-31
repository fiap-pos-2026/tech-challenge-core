package br.com.fiap.pos.tech_challenge.core.integration;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Emite tokens no formato da Lambda de autenticação por CPF, assinados com o par de teste
 * {@code certs/test-customer-*.pem}.
 */
final class CustomerTokens {

    static final String ISSUER = "tech-challenge-auth-lambda";
    static final String AUDIENCE = "tech-challenge-core";

    private CustomerTokens() {
    }

    static String valid(UUID customerUuid) {
        return sign(lambdaKey(), claims(customerUuid).build());
    }

    static String withAudience(UUID customerUuid, String audience) {
        return sign(lambdaKey(), claims(customerUuid).audience(List.of(audience)).build());
    }

    static String expired(UUID customerUuid) {
        var past = Instant.now().minus(Duration.ofHours(2));
        return sign(lambdaKey(), claims(customerUuid).issuedAt(past).expiresAt(past.plus(Duration.ofMinutes(30))).build());
    }

    static String signedWithUnknownKey(UUID customerUuid) {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            return sign(new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey(pair.getPrivate()).build(),
                    claims(customerUuid).build());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JwtClaimsSet.Builder claims(UUID customerUuid) {
        var now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(30)))
                .subject(customerUuid.toString())
                .id(UUID.randomUUID().toString())
                .claim("role", "CUSTOMER")
                .claim("name", "Cliente Teste");
    }

    private static String sign(RSAKey key, JwtClaimsSet claims) {
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        var header = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static RSAKey lambdaKey() {
        try (var priv = CustomerTokens.class.getResourceAsStream("/certs/test-customer-private.pem");
             var pub = CustomerTokens.class.getResourceAsStream("/certs/test-customer-public.pem")) {
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(priv);
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(pub);
            return new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
