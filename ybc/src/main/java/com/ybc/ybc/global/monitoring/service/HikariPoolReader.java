package com.ybc.ybc.global.monitoring.service;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Optional;

/** Reads the application's primary Hikari pool without opening another DB connection. */
@Component
public class HikariPoolReader {
    private final DataSource dataSource;
    public HikariPoolReader(DataSource dataSource) { this.dataSource = dataSource; }

    public Optional<Sample> read() {
        if (!(dataSource instanceof HikariDataSource hikari)) return Optional.empty();
        try {
            var pool = hikari.getHikariPoolMXBean();
            if (pool == null || hikari.isClosed()) return Optional.empty();
            int pending = pool.getThreadsAwaitingConnection();
            int active = pool.getActiveConnections();
            int max = hikari.getMaximumPoolSize();
            if (pending < 0 || active < 0 || max < 1) return Optional.empty();
            return Optional.of(new Sample(hikari.getPoolName(), pending, active, max));
        } catch (RuntimeException unavailable) { return Optional.empty(); }
    }

    public record Sample(String pool, int pending, int active, int maximum) { }
}
