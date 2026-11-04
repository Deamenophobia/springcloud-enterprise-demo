package com.example.commerce.labs;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;

class JdbcAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class, JdbcClientAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class))
        .withPropertyValues("spring.datasource.generate-unique-name=true");

    @Test
    void bootAssemblesWorkingJdbcInfrastructureFromTheClasspath() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(DataSource.class)
                .hasSingleBean(JdbcClient.class).hasSingleBean(PlatformTransactionManager.class);
            int answer = context.getBean(JdbcClient.class).sql("SELECT 42").query(Integer.class).single();
            assertThat(answer).isEqualTo(42);
        });
    }

    @Test
    void userDataSourceReplacesOnlyTheDefaultWhileJdbcStillWorks() {
        var supplied = new DriverManagerDataSource("jdbc:h2:mem:custom_source", "sa", "");
        runner.withBean("businessDataSource", DataSource.class, () -> supplied).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(DataSource.class).hasSingleBean(JdbcClient.class);
            assertThat(context.getBean(DataSource.class)).isSameAs(supplied);
            assertThat(context.getBean(JdbcClient.class).sql("SELECT 7").query(Integer.class).single()).isEqualTo(7);
        });
    }

    @Test
    void missingJdbcApiDisablesTheConditionalInfrastructure() {
        runner.withClassLoader(new FilteredClassLoader("org.springframework.jdbc")).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(DataSource.class).doesNotHaveBean(JdbcClient.class);
        });
    }
}
