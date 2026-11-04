package com.example.commerce.orders;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CatalogWiringTest {
    @Test void constructorReceivesTheSingleCatalogAdapter() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(OrderRepository.class, () -> mock(OrderRepository.class));
            context.registerBean(CatalogClient.class, () -> mock(CatalogClient.class));
            context.registerBean(TransactionTemplate.class, () -> new TransactionTemplate(mock(PlatformTransactionManager.class)));
            context.register(OrderService.class);
            context.refresh();
            assertThat(context.getBean(CatalogPort.class)).isSameAs(context.getBean(CatalogClient.class));
            assertThat(context.getBean(OrderService.class)).isNotNull();
        }
    }
}
