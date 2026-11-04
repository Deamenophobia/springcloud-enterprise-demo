package com.example.commerce.orders;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderPersistenceIT {
    private static final CatalogPort.Quote QUOTE =
        new CatalogPort.Quote("KEYBOARD-001", "Keyboard", new BigDecimal("89.00"), "USD");
    private String schema;
    private JdbcClient admin;
    private OrderRepository repository;
    private TransactionTemplate transactions;
    private OrderService service;
    @BeforeAll void setup() {
        String url = Objects.requireNonNull(System.getenv("TEST_DATABASE_URL"), "postgres-it requires TEST_DATABASE_URL");
        String user = Objects.requireNonNull(System.getenv("TEST_DATABASE_USER"), "postgres-it requires TEST_DATABASE_USER");
        String password = Objects.requireNonNull(System.getenv("TEST_DATABASE_PASSWORD"), "postgres-it requires TEST_DATABASE_PASSWORD");
        var source = new DriverManagerDataSource(url, user, password);
        admin = JdbcClient.create(source);
        schema = "it_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        var isolated = new DriverManagerDataSource(url, user, password);
        isolated.setConnectionProperties(properties);
        repository = new OrderRepository(JdbcClient.create(isolated));
        var catalog = mock(CatalogPort.class);
        when(catalog.quote(any(), any())).thenReturn(QUOTE);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(isolated));
        service = new OrderService(repository, catalog, transactions);
    }
    @AfterAll void cleanup() {
        if (admin != null && schema != null && schema.matches("it_[a-f0-9]{32}"))
            admin.sql("DROP SCHEMA " + schema + " CASCADE").update();
    }
    @Test void concurrentRequestsPersistExactlyOneOrder() throws Exception {
        String key = UUID.randomUUID().toString();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<OrderService.Result>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                start.await();
                return service.create("alice", key, new CreateOrder("KEYBOARD-001", 2, null), "token");
            }));
            start.countDown();
            List<OrderService.Result> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            assertThat(results.stream().filter(OrderService.Result::created).count()).isEqualTo(1);
            assertThat(results.stream().map(r -> r.order().id()).distinct().count()).isEqualTo(1);
            var saved = repository.findByKey("alice", key).orElseThrow();
            assertThat(saved.total()).isEqualByComparingTo("178.00");
            assertThat(repository.findById("bob", saved.id())).isEmpty();
        }
    }

    @Test void conflictingNotesRaceForOneIdempotencyKey() throws Exception {
        String key = UUID.randomUUID().toString();
        var bothPassedLookup = new CyclicBarrier(2);
        var catalog = mock(CatalogPort.class);
        when(catalog.quote(any(), any())).thenAnswer(call -> {
            bothPassedLookup.await(10, TimeUnit.SECONDS);
            return QUOTE;
        });
        var subject = new OrderService(repository, catalog, transactions);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> subject.create("alice", key,
                new CreateOrder("KEYBOARD-001", 2, "Leave at reception"), "token"));
            var second = pool.submit(() -> subject.create("alice", key,
                new CreateOrder("KEYBOARD-001", 2, "Leave at the side door"), "token"));

            var winners = new ArrayList<OrderService.Result>();
            int conflicts = 0;
            for (var attempt : List.of(first, second)) {
                try {
                    winners.add(attempt.get(20, TimeUnit.SECONDS));
                } catch (ExecutionException failure) {
                    assertThat(failure.getCause()).isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
                    conflicts++;
                }
            }

            assertThat(conflicts).isEqualTo(1);
            assertThat(winners).singleElement().satisfies(winner -> {
                assertThat(winner.created()).isTrue();
                var saved = repository.findByKey("alice", key).orElseThrow();
                assertThat(saved.id()).isEqualTo(winner.order().id());
                assertThat(saved.customerNote()).isEqualTo(winner.order().customerNote());
                assertThat(saved.customerNote()).isIn("Leave at reception", "Leave at the side door");
            });
        }
    }
}
