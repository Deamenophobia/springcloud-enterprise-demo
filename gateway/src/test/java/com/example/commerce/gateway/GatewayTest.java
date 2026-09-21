package com.example.commerce.gateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "management.server.port=0", "security.jwt.mode=demo", "security.jwt.issuer=commerce-demo",
    "security.jwt.audience=commerce-api", "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
class GatewayTest {
    @LocalServerPort int port;
    @Test void blocksUnauthenticatedRequestsBeforeRouting() {
        WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
            .get().uri("/api/products").exchange().expectStatus().isUnauthorized();
    }
}
