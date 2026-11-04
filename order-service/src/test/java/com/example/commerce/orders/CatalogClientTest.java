package com.example.commerce.orders;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.circuitbreaker.resilience4j.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class CatalogClientTest {
    private HttpServer server;
    private ExecutorService executor;
    private CatalogClient client;
    private final AtomicInteger status = new AtomicInteger(404);
    private final AtomicInteger requests = new AtomicInteger();
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/products/", exchange -> {
            requests.incrementAndGet();
            try {
                int responseStatus = status.get();
                if (responseStatus == 200) {
                    byte[] body = """
                        {"sku":"KEYBOARD-001","name":"Keyboard","price":89.00,"currency":"USD"}
                        """.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } else {
                    exchange.sendResponseHeaders(responseStatus, -1);
                }
            } finally {
                exchange.close();
            }
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
    private void expect(CatalogClient target, int remote, int local) {
        status.set(remote);
        assertThatThrownBy(() -> target.quote("KEYBOARD-001", "token")).isInstanceOfSatisfying(
            ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(local));
    }
    @Test void mapsMissingProductTo422ThroughRealCircuitBreaker() { expect(client, 404, 422); }
    @Test void preservesScopeFailureThroughRealCircuitBreaker() { expect(client, 403, 403); }
    @Test void mapsRemoteOutageTo503() { expect(client, 503, 503); }

    @Test void configuredBreakerStopsCallsUntilRecoveryProbe() throws Exception {
        var settings = new YamlPropertySourceLoader().load("order-config", new ClassPathResource("application.yaml"));
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CircuitBreakerAutoConfiguration.class,
                TimeLimiterAutoConfiguration.class, Resilience4JAutoConfiguration.class))
            .withInitializer(context -> settings.forEach(context.getEnvironment().getPropertySources()::addLast))
            .run(context -> {
                var factory = context.getBean(Resilience4JCircuitBreakerFactory.class);
                var breaker = context.getBean(CircuitBreakerRegistry.class).find("catalog").orElseThrow();
                var target = new CatalogClient(RestClient.builder(), factory, "http://127.0.0.1:" + server.getAddress().getPort());

                for (int call = 1; call < 5; call++) {
                    expect(target, 503, 503);
                    assertThat(breaker.getState()).isEqualTo(State.CLOSED);
                }
                expect(target, 503, 503);
                assertThat(breaker.getState()).isEqualTo(State.OPEN);
                assertThat(requests.get()).isEqualTo(5);

                expect(target, 503, 503);
                assertThat(requests.get()).isEqualTo(5);

                // Step into the recovery phase without sleeping through the production open interval.
                status.set(200);
                breaker.transitionToHalfOpenState();
                for (int probe = 1; probe <= 3; probe++) {
                    assertThat(breaker.getState()).isEqualTo(State.HALF_OPEN);
                    assertThat(target.quote("KEYBOARD-001", "token").price()).isEqualByComparingTo("89.00");
                }
                assertThat(breaker.getState()).isEqualTo(State.CLOSED);
                assertThat(requests.get()).isEqualTo(8);
            });
    }
}
