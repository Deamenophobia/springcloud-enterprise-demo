package com.example.commerce.catalog;
import java.math.BigDecimal;
public record Product(String sku, String name, BigDecimal price, String currency) {}
