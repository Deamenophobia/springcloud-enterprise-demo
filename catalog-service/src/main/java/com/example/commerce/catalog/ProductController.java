package com.example.commerce.catalog;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/products")
class ProductController {
    private final JdbcClient jdbc;
    ProductController(JdbcClient jdbc) { this.jdbc = jdbc; }
    @GetMapping
    List<Product> list(@RequestParam(defaultValue = "20") int limit, @RequestParam(defaultValue = "0") int offset) {
        if (limit < 1 || limit > 100 || offset < 0 || offset > 100000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use limit 1..100 and offset 0..100000");
        return jdbc.sql("SELECT sku, name, price, currency FROM products ORDER BY sku LIMIT :limit OFFSET :offset")
            .param("limit", limit).param("offset", offset).query(Product.class).list();
    }
    @GetMapping("/{sku}")
    Product get(@PathVariable String sku) {
        return jdbc.sql("SELECT sku, name, price, currency FROM products WHERE sku = :sku")
            .param("sku", sku).query(Product.class).optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }
}
