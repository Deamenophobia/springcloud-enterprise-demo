package com.example.commerce.orders;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Component
class CatalogClient {
    record Quote(String sku, String name, BigDecimal price, String currency) {}
    private final RestClient client;
    private final CircuitBreakerFactory<?, ?> breakers;
    CatalogClient(RestClient.Builder builder, CircuitBreakerFactory<?, ?> breakers,
                  @Value("${catalog.url}") String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.client = builder.baseUrl(url).requestFactory(factory).build();
        this.breakers = breakers;
    }
    Quote quote(String sku, String token) {
        return breakers.create("catalog").run(() -> {
            var quote = client.get().uri("/api/products/{sku}", sku)
                .headers(headers -> headers.setBearerAuth(token)).retrieve().body(Quote.class);
            if (quote == null || !sku.equals(quote.sku()) || quote.price() == null
                    || quote.price().signum() <= 0 || !"USD".equals(quote.currency()))
                throw new IllegalStateException("Invalid catalog response");
            return quote;
        }, error -> { throw mapFailure(error); });
    }
    static ResponseStatusException mapFailure(Throwable error) {
        // Circuit-breaker executor/bulkhead adapters can wrap the HTTP exception.
        while ((error instanceof java.util.concurrent.ExecutionException
                || error instanceof java.util.concurrent.CompletionException) && error.getCause() != null)
            error = error.getCause();
        if (error instanceof RestClientResponseException response) {
            if (response.getStatusCode().value() == 404)
                return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown product");
            if (response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403)
                return new ResponseStatusException(HttpStatus.FORBIDDEN, "A valid catalog.read scope is also required");
        }
        org.slf4j.LoggerFactory.getLogger(CatalogClient.class).warn("Catalog unavailable: type={}, causeType={}",
            error.getClass().getName(), error.getCause() == null ? "none" : error.getCause().getClass().getName());
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog temporarily unavailable");
    }
}
