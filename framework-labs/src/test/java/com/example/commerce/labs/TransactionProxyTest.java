package com.example.commerce.labs;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class TransactionProxyTest {
    private AnnotationConfigApplicationContext context;
    private JdbcClient jdbc;
    private Ledger ledger;

    @BeforeEach
    void start() {
        context = new AnnotationConfigApplicationContext(TransactionConfiguration.class);
        jdbc = context.getBean(JdbcClient.class);
        jdbc.sql("CREATE TABLE ledger (id INTEGER PRIMARY KEY)").update();
        ledger = context.getBean(Ledger.class);
        assertThat(AopUtils.isAopProxy(ledger)).isTrue();
        // Deliberately no test-managed transaction: row counts reveal real commits/rollbacks.
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @AfterEach
    void stop() {
        if (context != null) context.close();
    }

    @Test
    void externalCallRollsBackOnUncheckedFailure() {
        assertThatThrownBy(ledger::writeThenFail).isInstanceOf(IllegalStateException.class);
        assertThat(context.getBean(AtomicBoolean.class)).isTrue();
        assertThat(rowCount()).isZero();
    }

    @Test
    void selfInvocationBypassesAdviceAndLeavesAnAutocommittedRow() {
        assertThatThrownBy(ledger::callSelf).isInstanceOf(IllegalStateException.class);
        assertThat(context.getBean(AtomicBoolean.class)).isFalse();
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void callingAnInjectedCollaboratorRestoresTheTransactionBoundary() {
        assertThatThrownBy(context.getBean(Checkout.class)::submit).isInstanceOf(IllegalStateException.class);
        assertThat(context.getBean(AtomicBoolean.class)).isTrue();
        assertThat(rowCount()).isZero();
    }

    @Test
    void checkedFailureCommitsUnderTheDefaultRollbackPolicy() {
        assertThatThrownBy(ledger::checkedFailure).isInstanceOf(IOException.class);
        assertThat(context.getBean(AtomicBoolean.class)).isTrue();
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void explicitRollbackRuleRollsBackACheckedFailure() {
        assertThatThrownBy(ledger::checkedFailureWithRollback).isInstanceOf(IOException.class);
        assertThat(context.getBean(AtomicBoolean.class)).isTrue();
        assertThat(rowCount()).isZero();
    }

    @Test
    void transactionTemplateRollsBackWithoutAServiceProxy() {
        var template = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(() -> template.executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            jdbc.sql("INSERT INTO ledger VALUES (1)").update();
            throw new IllegalStateException("simulated downstream failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(rowCount()).isZero();
    }

    private int rowCount() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        return jdbc.sql("SELECT COUNT(*) FROM ledger").query(Integer.class).single();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionConfiguration {
        @Bean(destroyMethod = "shutdown") EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }
        @Bean JdbcClient jdbc(DataSource dataSource) { return JdbcClient.create(dataSource); }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean AtomicBoolean transactionObserved() { return new AtomicBoolean(); }
        @Bean Ledger ledger(JdbcClient jdbc, AtomicBoolean transactionObserved) {
            return new Ledger(jdbc, transactionObserved);
        }
        @Bean Checkout checkout(Ledger ledger) { return new Checkout(ledger); }
    }

    public static class Ledger {
        private final JdbcClient jdbc;
        private final AtomicBoolean transactionObserved;
        Ledger(JdbcClient jdbc, AtomicBoolean transactionObserved) {
            this.jdbc = jdbc;
            this.transactionObserved = transactionObserved;
        }
        private void insert() {
            transactionObserved.set(TransactionSynchronizationManager.isActualTransactionActive());
            jdbc.sql("INSERT INTO ledger VALUES (1)").update();
        }
        @Transactional
        public void writeThenFail() { insert(); throw new IllegalStateException("simulated downstream failure"); }
        public void callSelf() { writeThenFail(); }
        @Transactional
        public void checkedFailure() throws IOException { insert(); throw new IOException("simulated checked failure"); }
        @Transactional(rollbackFor = IOException.class)
        public void checkedFailureWithRollback() throws IOException {
            insert(); throw new IOException("simulated checked failure");
        }
    }

    static class Checkout {
        private final Ledger ledger;
        Checkout(Ledger ledger) { this.ledger = ledger; }
        void submit() { ledger.writeThenFail(); }
    }
}
