package com.onze.api;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One PostgreSQL process for the integration suite, with clean data for each test class. */
public abstract class IntegrationTestSupport {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("onze_integration_test")
            .withUsername("onze")
            .withPassword("onze");

    static {
        // Do not use @Container here: the JUnit extension stops static containers after each class.
        POSTGRES.start();
    }

    @DynamicPropertySource
    protected static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeAll
    protected static void clearPreviousClassData() throws SQLException {
        // Flyway may not have run yet for the first class. Keep its history for later contexts.
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (ResultSet result = statement.executeQuery(
                    "SELECT tablename FROM pg_tables WHERE schemaname = current_schema() "
                            + "AND tablename <> 'flyway_schema_history'")) {
                while (result.next()) {
                    tables.add("\"" + result.getString(1).replace("\"", "\"\"") + "\"");
                }
            }
            if (!tables.isEmpty()) {
                statement.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
            }
        }
    }
}
