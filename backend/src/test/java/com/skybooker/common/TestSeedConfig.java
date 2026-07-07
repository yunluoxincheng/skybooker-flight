package com.skybooker.common;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Test-only baseline data that replaces the old aviation demo rows removed from Flyway.
 */
@Configuration
@Profile("test")
public class TestSeedConfig {

    @Bean
    ApplicationRunner skyBookerTestBaseline(DataSource dataSource) {
        return args -> {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                    new ClassPathResource("test-fixtures/base-air-data.sql"));
            populator.setSqlScriptEncoding("UTF-8");
            populator.execute(dataSource);
        };
    }
}
