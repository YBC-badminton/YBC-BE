package com.ybc.ybc.global.monitoring.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HikariPoolReaderTest {
    @Test void uninitializedOrUnsupportedPoolStaysUnknownWithoutOpeningConnections() throws Exception {
        var unsupported = mock(DataSource.class);
        assertTrue(new HikariPoolReader(unsupported).read().isEmpty());
        verifyNoInteractions(unsupported);
        try (var hikari = new HikariDataSource()) {
            assertTrue(new HikariPoolReader(hikari).read().isEmpty());
        }
    }

    @Test void readsActualWaitingThreadFromAnExhaustedHikariPool() throws Exception {
        var config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:pool-monitor-test");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setPoolName("primary-test");
        var executor = Executors.newSingleThreadExecutor();
        try (var dataSource = new HikariDataSource(config); var held = dataSource.getConnection()) {
            var waiting = executor.submit(() -> {
                try (var acquired = dataSource.getConnection()) { return acquired.isValid(1); }
            });
            var reader = new HikariPoolReader(dataSource);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            HikariPoolReader.Sample sample;
            do {
                sample = reader.read().orElseThrow();
                if (sample.pending() > 0) break;
                Thread.sleep(10);
            } while (System.nanoTime() < deadline);
            assertEquals(1, sample.pending());
            assertEquals(1, sample.active());
            assertEquals(1, sample.maximum());
            assertEquals("primary-test", sample.pool());
            held.close();
            assertTrue(waiting.get(3, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
}
