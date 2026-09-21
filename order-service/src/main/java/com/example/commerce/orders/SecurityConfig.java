package com.example.commerce.orders;
import com.example.commerce.security.TokenPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(
            @Value("${security.jwt.mode:oidc}") String mode,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience,
            @Value("${security.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${security.jwt.secret:}") String secret) {
        NimbusJwtDecoder decoder;
        if ("demo".equals(mode)) {
            decoder = NimbusJwtDecoder.withSecretKey(TokenPolicy.secretKey(secret)).macAlgorithm(MacAlgorithm.HS256).build();
        } else if ("oidc".equals(mode) && jwkSetUri.startsWith("https://") && issuer.startsWith("https://")) {
            decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        } else {
            throw new IllegalArgumentException("Configure HTTPS JWT_ISSUER and JWT_JWK_SET_URI, or explicitly select JWT_MODE=demo");
        }
        decoder.setJwtValidator(TokenPolicy.validator(issuer, audience));
        return decoder;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/prometheus").hasAuthority("SCOPE_metrics.read")
                .requestMatchers(HttpMethod.POST, "/api/orders").hasAuthority("SCOPE_orders.write")
                .requestMatchers(HttpMethod.GET, "/api/orders/**").hasAuthority("SCOPE_orders.read")
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults())).build();
    }
}
