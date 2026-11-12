package com.dium.demo.db;

import com.dium.demo.support.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Dev-профиль: миграции вместе с тестовыми данными накатываются на пустую базу без ошибок. */
class DevSeedMigrationTest {
    private static final String DATABASE = "dev_seed_check";

    @Test
    void migrationsAndDevSeedApplyToEmptyDatabase() throws Exception {
        String adminUrl = EmbeddedPostgres.jdbcUrl();
        try (Connection connection = DriverManager.getConnection(adminUrl, EmbeddedPostgres.username(), "");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
            statement.execute("CREATE DATABASE " + DATABASE);
        }
        String url = adminUrl.replace("/postgres?", "/" + DATABASE + "?");
        assertThat(url).contains(DATABASE);

        Flyway flyway = Flyway.configure()
                .dataSource(url, EmbeddedPostgres.username(), "")
                .locations("classpath:db/migration", "classpath:db/seed/dev")
                .placeholders(Map.of("admin_phone", "", "admin_name", "", "admin_password_hash", ""))
                .load();

        assertThat(flyway.migrate().success).isTrue();
        // Повторный запуск ничего не меняет и не падает
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        try (Connection connection = DriverManager.getConnection(url, EmbeddedPostgres.username(), "");
             Statement statement = connection.createStatement();
             ResultSet venues = statement.executeQuery("SELECT count(*) FROM venues")) {
            venues.next();
            assertThat(venues.getInt(1)).isPositive();
        }
    }
}
