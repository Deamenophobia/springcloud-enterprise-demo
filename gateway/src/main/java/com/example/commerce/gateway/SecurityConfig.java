package com.example.commerce.gateway;
import com.example.commerce.security.TokenPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration(proxyBeanMethods = false)
class SecurityConfig {
    @Bean
    ReactiveJwtDecoder jwtDecoder(
            @Value("${security.jwt.mode:oidc}") String mode,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience,
            @Value("${security.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${security.jwt.secret:}") String secret) {
        NimbusReactiveJwtDecoder decoder;
        if ("demo".equals(mode)) {
            decoder = NimbusReactiveJwtDecoder.withSecretKey(TokenPolicy.secretKey(secret)).macAlgorithm(MacAlgorithm.HS256).build();
        } else if ("oidc".equals(mode) && jwkSetUri.startsWith("https://") && issuer.startsWith("https://")) {
            decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        } else {
            throw new IllegalArgumentException("Configure HTTPS JWT_ISSUER and JWT_JWK_SET_URI, or explicitly select JWT_MODE=demo");
        }
        decoder.setJwtValidator(TokenPolicy.validator(issuer, audience));
        return decoder;
    }

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .authorizeExchange(auth -> auth
                .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .pathMatchers("/actuator/prometheus").hasAuthority("SCOPE_metrics.read")
                .pathMatchers(HttpMethod.GET, "/api/products", "/api/products/**").hasAuthority("SCOPE_catalog.read")
                .pathMatchers(HttpMethod.POST, "/api/orders").hasAuthority("SCOPE_orders.write")
                .pathMatchers(HttpMethod.GET, "/api/orders/**").hasAuthority("SCOPE_orders.read")
                .anyExchange().denyAll())
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults())).build();
    }
}
