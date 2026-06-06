package com.systeam.testutil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class TestDatabaseHelper {

    private TestDatabaseHelper() {}

    public static DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:tc:postgresql:15-alpine:///testdb");
        config.setDriverClassName("org.testcontainers.jdbc.ContainerDatabaseDriver");
        config.setUsername("test");
        config.setPassword("test");
        return new HikariDataSource(config);
    }

    public static void executeSchema(JdbcTemplate jdbc) {
        String schema = new BufferedReader(new InputStreamReader(
            TestDatabaseHelper.class.getResourceAsStream("/schema-it.sql"),
            StandardCharsets.UTF_8
        )).lines().collect(Collectors.joining("\n"));
        jdbc.execute(schema);
    }

    public static void closeDataSource(DataSource ds) {
        if (ds instanceof HikariDataSource hds) {
            hds.close();
        }
    }
}
