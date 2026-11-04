package com.example.commerce.orders;
import jakarta.validation.constraints.*;
public record CreateOrder(
    @NotBlank @Pattern(regexp = "[A-Z0-9-]{1,64}") String sku,
    @Min(1) @Max(100) int quantity,
    @Size(max = 500) String customerNote) {}
