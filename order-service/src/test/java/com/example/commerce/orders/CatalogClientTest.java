package com.example.commerce.orders;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.cloud.circuitbreaker.resilience4j.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class CatalogClientTest {
    private HttpServer server;
    private ExecutorService executor;
    private CatalogClient client;
    private final AtomicInteger status = new AtomicInteger(404);
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/products/", exchange -> {
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        var properties = new Resilience4JConfigurationProperties();
        properties.setDisableTimeLimiter(true);
        // Keep the executor adapter enabled here to exercise wrapped HTTP failures.
        var factory = new Resilience4JCircuitBreakerFactory(
            CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(), null, properties);
        executor = Executors.newFixedThreadPool(1);
        factory.configureExecutorService(executor);
        client = new CatalogClient(RestClient.builder(), factory, "http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach void stop() {
        server.stop(0);
        executor.shutdownNow();
    }
    private void expect(int remote, int local) {
        status.set(remote);
        assertThatThrownBy(() -> client.quote("MISSING", "token")).isInstanceOfSatisfying(
            ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(local));
    }
    @Test void mapsMissingProductTo422ThroughRealCircuitBreaker() { expect(404, 422); }
    @Test void preservesScopeFailureThroughRealCircuitBreaker() { expect(403, 403); }
    @Test void mapsRemoteOutageTo503() { expect(503, 503); }
}
