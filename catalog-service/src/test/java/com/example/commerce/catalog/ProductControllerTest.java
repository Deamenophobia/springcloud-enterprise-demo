package com.example.commerce.catalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class, properties = {
    "security.jwt.mode=demo", "security.jwt.issuer=commerce-demo", "security.jwt.audience=commerce-api",
    "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
@Import(SecurityConfig.class)
class ProductControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean JdbcClient jdbc;
    @Test void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        verifyNoInteractions(jdbc);
    }
    @Test void rejectsUnboundedPageSize() throws Exception {
        mvc.perform(get("/api/products?limit=10000").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalog.read"))))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc);
    }
}
