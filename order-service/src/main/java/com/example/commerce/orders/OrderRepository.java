package com.example.commerce.orders;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class OrderRepository {
    private static final String COLUMNS = "id, customer_id, idempotency_key, sku, quantity, unit_price, total, currency, created_at";
    private final JdbcClient jdbc;
    OrderRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    Optional<PurchaseOrder> findByKey(String customer, String key) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM orders WHERE customer_id=:customer AND idempotency_key=:key")
            .param("customer", customer).param("key", key).query(PurchaseOrder.class).optional();
    }
    Optional<PurchaseOrder> findById(String customer, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM orders WHERE customer_id=:customer AND id=:id")
            .param("customer", customer).param("id", id).query(PurchaseOrder.class).optional();
    }
    int insert(PurchaseOrder order) {
        return jdbc.sql("""
            INSERT INTO orders (id, customer_id, idempotency_key, sku, quantity, unit_price, total, currency, created_at)
            VALUES (:id, :customer, :key, :sku, :quantity, :price, :total, :currency, :created)
            ON CONFLICT (customer_id, idempotency_key) DO NOTHING
            """)
            .param("id", order.id()).param("customer", order.customerId()).param("key", order.idempotencyKey())
            .param("sku", order.sku()).param("quantity", order.quantity()).param("price", order.unitPrice())
            .param("total", order.total()).param("currency", order.currency()).param("created", order.createdAt()).update();
    }
}
