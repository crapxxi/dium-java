package com.dium.demo.support;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Один встроенный PostgreSQL на весь прогон тестов. Запускается при первом обращении
 * и останавливается вместе с JVM.
 */
public final class EmbeddedPostgres {
    private static final String DATABASE = "postgres";
    private static final String USER = "postgres";

    private static io.zonky.test.db.postgres.embedded.EmbeddedPostgres instance;

    private EmbeddedPostgres() {
    }

    public static synchronized String jdbcUrl() {
        if (instance == null) {
            instance = start();
        }
        return instance.getJdbcUrl(USER, DATABASE);
    }

    public static String username() {
        return USER;
    }

    private static io.zonky.test.db.postgres.embedded.EmbeddedPostgres start() {
        try {
            var postgres = io.zonky.test.db.postgres.embedded.EmbeddedPostgres.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // JVM завершается, останавливать больше нечего
                }
            }));
            return postgres;
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось запустить встроенный PostgreSQL", e);
        }
    }
}
