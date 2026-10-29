package com.example.commerce.orders;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    OrderRepository repository = mock(OrderRepository.class);
    CatalogPort catalog = mock(CatalogPort.class);
    OrderService service;
    PurchaseOrder existing = new PurchaseOrder(UUID.randomUUID(), "alice", "request-001", "KEYBOARD-001", 2,
        new BigDecimal("89.00"), new BigDecimal("178.00"), "USD", OffsetDateTime.now());
    @BeforeEach void setup() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new OrderService(repository, catalog, new TransactionTemplate(manager));
    }
    @Test void replayWorksWithoutCatalogAndDoesNotInsertAgain() {
        when(repository.findByKey("alice", "request-001")).thenReturn(Optional.of(existing));
        var result = service.create("alice", "request-001", new CreateOrder("KEYBOARD-001", 2), "token");
        assertThat(result.created()).isFalse();
        assertThat(result.order().id()).isEqualTo(existing.id());
        verifyNoInteractions(catalog);
        verify(repository, never()).insert(any());
    }
    @Test void conflictingRequestReturns409() {
        when(repository.findByKey("alice", "request-001")).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.create("alice", "request-001", new CreateOrder("KEYBOARD-001", 3), "token"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }
    @Test void totalUsesServerPrice() {
        when(repository.findByKey(any(), any())).thenReturn(Optional.empty());
        when(catalog.quote("KEYBOARD-001", "token")).thenReturn(new CatalogPort.Quote("KEYBOARD-001", "Keyboard", new BigDecimal("89.00"), "USD"));
        when(repository.insert(any())).thenReturn(1);
        var result = service.create("alice", "request-001", new CreateOrder("KEYBOARD-001", 2), "token");
        assertThat(result.created()).isTrue();
        assertThat(result.order().total()).isEqualByComparingTo("178.00");
    }
    @Test void concurrentLoserReturnsWinnersOrder() {
        when(repository.findByKey("alice", "request-001")).thenReturn(Optional.empty(), Optional.of(existing));
        when(catalog.quote(any(), any())).thenReturn(new CatalogPort.Quote("KEYBOARD-001", "Keyboard", new BigDecimal("99.00"), "USD"));
        when(repository.insert(any())).thenReturn(0);
        var result = service.create("alice", "request-001", new CreateOrder("KEYBOARD-001", 2), "token");
        assertThat(result.order()).isEqualTo(existing);
        assertThat(result.created()).isFalse();
    }
    @Test void catalogFailureLeavesPersistenceAndTransactionUntouched() {
        when(repository.findByKey(any(), any())).thenReturn(Optional.empty());
        when(catalog.quote(any(), any())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        var manager = mock(PlatformTransactionManager.class);
        var subject = new OrderService(repository, catalog, new TransactionTemplate(manager));
        assertThatThrownBy(() -> subject.create("alice", "request-001", new CreateOrder("KEYBOARD-001", 2), "token"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        verify(repository, never()).insert(any());
        verifyNoInteractions(manager);
    }
    @Test void readIsScopedToCustomer() {
        when(repository.findById("bob", existing.id())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("bob", existing.id())).isInstanceOfSatisfying(
            ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }
}
