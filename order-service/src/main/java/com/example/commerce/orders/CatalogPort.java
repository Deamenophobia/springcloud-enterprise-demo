package com.example.commerce.orders;
import java.math.BigDecimal;

/** The order application's requirement for a validated product quote. */
interface CatalogPort {
    record Quote(String sku, String name, BigDecimal price, String currency) {}
    Quote quote(String sku, String token);
}
