package com.example.commerce.orders;
import java.net.URI;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders")
class OrderController {
    private final OrderService service;
    OrderController(OrderService service) { this.service = service; }
    @PostMapping
    ResponseEntity<PurchaseOrder> create(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreateOrder request, @AuthenticationPrincipal Jwt jwt) {
        if (!key.matches("[A-Za-z0-9_-]{8,128}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be 8..128 letters, digits, underscores or hyphens");
        var result = service.create(jwt.getSubject(), key, request, jwt.getTokenValue());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
            .location(URI.create("/api/orders/" + result.order().id()))
            .header("Idempotency-Replayed", Boolean.toString(!result.created())).body(result.order());
    }
    @GetMapping("/{id}")
    PurchaseOrder get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.get(jwt.getSubject(), id);
    }
}
