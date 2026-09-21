package com.example.commerce.orders;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
class OrderService {
    record Result(PurchaseOrder order, boolean created) {}
    private final OrderRepository repository;
    private final CatalogClient catalog;
    private final TransactionTemplate transactions;
    OrderService(OrderRepository repository, CatalogClient catalog, TransactionTemplate transactions) {
        this.repository = repository; this.catalog = catalog; this.transactions = transactions;
    }
    Result create(String customer, String key, CreateOrder request, String token) {
        var existing = repository.findByKey(customer, key);
        if (existing.isPresent()) return replay(existing.get(), request);
        // Remote I/O occurs before the transaction; no connection is held while waiting for HTTP.
        var quote = catalog.quote(request.sku(), token);
        var candidate = new PurchaseOrder(UUID.randomUUID(), customer, key, request.sku(), request.quantity(),
            quote.price(), quote.price().multiply(java.math.BigDecimal.valueOf(request.quantity())),
            quote.currency(), OffsetDateTime.now(java.time.ZoneOffset.UTC));
        return transactions.execute(status -> {
            if (repository.insert(candidate) == 1) return new Result(candidate, true);
            // READ COMMITTED sees the winning concurrent insert in this next statement.
            return replay(repository.findByKey(customer, key).orElseThrow(), request);
        });
    }
    private Result replay(PurchaseOrder order, CreateOrder request) {
        if (!order.sku().equals(request.sku()) || order.quantity() != request.quantity())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used with a different request");
        return new Result(order, false);
    }
    PurchaseOrder get(String customer, UUID id) {
        return repository.findById(customer, id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }
}
