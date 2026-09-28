package com.example.commerce.labs;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import static org.assertj.core.api.Assertions.*;

class BeanContainerTest {
    @Test
    void qualifierSelectsTheIntendedConstructorDependency() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("card", PaymentPort.class, () -> () -> "card");
            context.registerBean("invoice", PaymentPort.class, () -> () -> "invoice");
            context.register(QualifiedCheckout.class);
            context.refresh();

            var checkout = context.getBean(QualifiedCheckout.class);
            assertThat(checkout.port).isSameAs(context.getBean("card"));
            assertThat(checkout.port.pay()).isEqualTo("card");
        }
    }

    @Test
    void ambiguousDependencyPreventsContextStartup() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("card", PaymentPort.class, () -> () -> "card");
            context.registerBean("invoice", PaymentPort.class, () -> () -> "invoice");
            context.register(AmbiguousCheckout.class);

            assertThatThrownBy(context::refresh)
                .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class);
            assertThat(context.isActive()).isFalse();
        }
    }

    @Test
    void providerResolvesNewPrototypesInsideOneSingleton() {
        try (var context = new AnnotationConfigApplicationContext(ScopeConfiguration.class)) {
            var checkout = context.getBean(ScopedCheckout.class);
            var first = checkout.tasks.getObject();
            var second = checkout.tasks.getObject();

            assertThat(context.getBean(ScopedCheckout.class)).isSameAs(checkout);
            assertThat(first).isNotSameAs(second).isNotSameAs(checkout.injectedOnce);
            assertThat(context.getBean(ScopedCheckout.class).injectedOnce).isSameAs(checkout.injectedOnce);
        }
    }

    @Test
    void containerInitializesAndDestroysItsSingleton() {
        List<String> events = new ArrayList<>();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("resource", ManagedResource.class, () -> new ManagedResource(events), definition -> {
                definition.setInitMethodName("start");
                definition.setDestroyMethodName("stop");
            });
            context.refresh();
            assertThat(context.getBean(ManagedResource.class)).isNotNull();
            assertThat(events).containsExactly("constructed", "started");
        }
        assertThat(events).containsExactly("constructed", "started", "stopped");
    }

    @Test
    void fullConfigurationInterceptsCallsBetweenBeanMethods() {
        try (var context = new AnnotationConfigApplicationContext(FullConfiguration.class)) {
            assertThat(context.getBean(Holder.class).dependency()).isSameAs(context.getBean(Dependency.class));
        }
    }

    @Test
    void liteConfigurationDirectCallCreatesAnUnmanagedExtraObject() {
        try (var context = new AnnotationConfigApplicationContext(LiteConfiguration.class)) {
            assertThat(context.getBean(Holder.class).dependency()).isNotSameAs(context.getBean(Dependency.class));
        }
    }

    @Test
    void methodParameterInjectionPreservesIdentityWithoutConfigurationProxy() {
        try (var context = new AnnotationConfigApplicationContext(InjectedConfiguration.class)) {
            assertThat(context.getBean(Holder.class).dependency()).isSameAs(context.getBean(Dependency.class));
        }
    }

    interface PaymentPort { String pay(); }

    static class QualifiedCheckout {
        final PaymentPort port;
        QualifiedCheckout(@Qualifier("card") PaymentPort port) { this.port = port; }
    }

    static class AmbiguousCheckout {
        AmbiguousCheckout(PaymentPort port) {}
    }

    static class Task {}
    record ScopedCheckout(Task injectedOnce, ObjectProvider<Task> tasks) {}

    @Configuration(proxyBeanMethods = false)
    static class ScopeConfiguration {
        @Bean @Scope("prototype") Task task() { return new Task(); }
        @Bean ScopedCheckout checkout(Task task, ObjectProvider<Task> tasks) {
            return new ScopedCheckout(task, tasks);
        }
    }

    static class ManagedResource {
        private final List<String> events;
        ManagedResource(List<String> events) { this.events = events; events.add("constructed"); }
        public void start() { events.add("started"); }
        public void stop() { events.add("stopped"); }
    }

    static class Dependency {}
    record Holder(Dependency dependency) {}

    @Configuration
    static class FullConfiguration {
        @Bean Dependency dependency() { return new Dependency(); }
        @Bean Holder holder() { return new Holder(dependency()); }
    }

    @Configuration(proxyBeanMethods = false)
    static class LiteConfiguration {
        @Bean Dependency dependency() { return new Dependency(); }
        @Bean Holder holder() { return new Holder(dependency()); }
    }

    @Configuration(proxyBeanMethods = false)
    static class InjectedConfiguration {
        @Bean Dependency dependency() { return new Dependency(); }
        @Bean Holder holder(Dependency dependency) { return new Holder(dependency); }
    }
}
