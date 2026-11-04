package com.example.commerce.orders;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
public record PurchaseOrder(UUID id, String customerId, String idempotencyKey, String sku, int quantity,
                            BigDecimal unitPrice, BigDecimal total, String currency, OffsetDateTime createdAt,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String customerNote) {}
