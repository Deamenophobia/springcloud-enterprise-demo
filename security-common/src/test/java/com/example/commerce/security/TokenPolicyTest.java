package com.example.commerce.security;
import java.time.Instant;
import java.util.List;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

class TokenPolicyTest {
    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private String token(String issuer, String audience, Instant expires, String subject) {
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
            .subject(subject).issuedAt(Instant.now().minusSeconds(120)).expiresAt(expires).build();
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(TokenPolicy.secretKey(KEY)));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    private NimbusJwtDecoder decoder() {
        var decoder = NimbusJwtDecoder.withSecretKey(TokenPolicy.secretKey(KEY)).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(TokenPolicy.validator("commerce-demo", "commerce-api"));
        return decoder;
    }
    @Test void acceptsValidSignedToken() {
        assertThat(decoder().decode(token("commerce-demo", "commerce-api", Instant.now().plusSeconds(300), "alice")).getSubject()).isEqualTo("alice");
    }
    @Test void rejectsWrongAudience() {
        assertThatThrownBy(() -> decoder().decode(token("commerce-demo", "other-api", Instant.now().plusSeconds(300), "alice"))).isInstanceOf(JwtValidationException.class);
    }
    @Test void rejectsWrongIssuer() {
        assertThatThrownBy(() -> decoder().decode(token("someone-else", "commerce-api", Instant.now().plusSeconds(300), "alice"))).isInstanceOf(JwtValidationException.class);
    }
    @Test void rejectsExpiredToken() {
        assertThatThrownBy(() -> decoder().decode(token("commerce-demo", "commerce-api", Instant.now().minusSeconds(90), "alice"))).isInstanceOf(JwtValidationException.class);
    }
    @Test void rejectsBlankSubject() {
        assertThatThrownBy(() -> decoder().decode(token("commerce-demo", "commerce-api", Instant.now().plusSeconds(300), ""))).isInstanceOf(JwtValidationException.class);
    }
    @Test void rejectsWeakKey() {
        assertThatThrownBy(() -> TokenPolicy.secretKey("YWJj")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsForgedSignature() {
        var token = token("commerce-demo", "commerce-api", Instant.now().plusSeconds(300), "alice");
        var parts = token.split("\\.");
        assertThatThrownBy(() -> decoder().decode(parts[0] + "." + parts[1] + "." + "A".repeat(43))).isInstanceOf(JwtException.class);
    }
}
