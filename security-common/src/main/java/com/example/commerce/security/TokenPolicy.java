package com.example.commerce.security;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

public final class TokenPolicy {
    private TokenPolicy() {}
    public static SecretKey secretKey(String encoded) {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT secret must contain at least 32 random bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    public static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            boolean valid = jwt.getAudience().contains(audience)
                && jwt.getSubject() != null && !jwt.getSubject().isBlank() && jwt.getSubject().length() <= 255
                && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null;
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Required token claims missing or invalid", null));
        };
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), claims);
    }
}
