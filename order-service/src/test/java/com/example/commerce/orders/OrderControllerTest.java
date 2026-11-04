package com.example.commerce.orders;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OrderController.class, properties = {
    "security.jwt.mode=demo", "security.jwt.issuer=commerce-demo", "security.jwt.audience=commerce-api",
    "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
@Import(SecurityConfig.class)
class OrderControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrderService service;
    @Test void unauthenticatedIs401() throws Exception {
        mvc.perform(get("/api/orders/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
    @Test void wrongScopeIs403() throws Exception {
        mvc.perform(post("/api/orders").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders.read")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"KEYBOARD-001\",\"quantity\":2}")
            .header("Idempotency-Key", "request-001")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void invalidQuantityIs400() throws Exception {
        mvc.perform(post("/api/orders").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders.write")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"KEYBOARD-001\",\"quantity\":0}")
            .header("Idempotency-Key", "request-001"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors.length()").value(1))
            .andExpect(jsonPath("$.errors[0].field").value("quantity"))
            .andExpect(jsonPath("$.errors[0].code").value("Min"))
            .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
        verifyNoInteractions(service);
    }
    @Test void missingKeyIs400() throws Exception {
        mvc.perform(post("/api/orders").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders.write")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"KEYBOARD-001\",\"quantity\":2}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").doesNotExist());
        verifyNoInteractions(service);
    }
    @Test void createdOrderHasLocationAndOwnerFromToken() throws Exception {
        var order = new PurchaseOrder(UUID.randomUUID(), "alice", "request-001", "KEYBOARD-001", 2,
            new BigDecimal("89.00"), new BigDecimal("178.00"), "USD", OffsetDateTime.now());
        when(service.create(eq("alice"), eq("request-001"), any(), any())).thenReturn(new OrderService.Result(order, true));
        mvc.perform(post("/api/orders").with(jwt().jwt(j -> j.subject("alice")).authorities(new SimpleGrantedAuthority("SCOPE_orders.write")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"KEYBOARD-001\",\"quantity\":2}")
            .header("Idempotency-Key", "request-001"))
            .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/orders/" + order.id()))
            .andExpect(jsonPath("$.total").value(178)).andExpect(jsonPath("$.customerId").value("alice"));
    }
}
