package com.coreeng.supportbot.summary;

import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Shared wiring for the Postgres-backed summary repository tests. */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
public class SummaryPostgresTestConfig {

    @Bean
    DataSource dataSource() {
        String url = System.getProperty("supportbot.localDb.url", "jdbc:postgresql://localhost:5432/postgres");
        String username = System.getProperty("supportbot.localDb.user", "postgres");
        String password = System.getProperty("supportbot.localDb.password", "postgres");
        return new DriverManagerDataSource(url, username, password);
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    DSLContext dslContext(DataSource dataSource) {
        return DSL.using(new TransactionAwareDataSourceProxy(dataSource), SQLDialect.POSTGRES);
    }

    @Bean
    SummaryReadRepository summaryReadRepository(DSLContext dslContext) {
        return new JdbcSummaryReadRepository(dslContext);
    }

    @Bean
    SummarySnapshotRepository summarySnapshotRepository(DSLContext dslContext) {
        return new JdbcSummarySnapshotRepository(dslContext);
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
