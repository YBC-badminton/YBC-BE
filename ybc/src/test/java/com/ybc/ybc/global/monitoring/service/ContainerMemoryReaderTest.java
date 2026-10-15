package com.ybc.ybc.global.monitoring.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ContainerMemoryReaderTest {
    @TempDir Path root;

    private ContainerMemoryReader reader(String membership) throws Exception {
        Path file = root.resolve("membership");
        Files.writeString(file, membership);
        return new ContainerMemoryReader(root, file);
    }

    @Test void readsCgroupV2UsageAndItsOwnLimitAtNamespacedRoot() throws Exception {
        Files.writeString(root.resolve("memory.current"), "800000000\n");
        Files.writeString(root.resolve("memory.max"), "1073741824\n");
        var sample = reader("0::/\n").read().orElseThrow();
        assertEquals(800_000_000L, sample.usedBytes());
        assertEquals(1_073_741_824L, sample.limitBytes());
        assertEquals("CGROUP_V2", sample.scope());
    }

    @Test void resolvesCurrentProcessGroupInsteadOfUsingParentHostCounters() throws Exception {
        Path group = Files.createDirectories(root.resolve("service/instance"));
        Files.writeString(group.resolve("memory.current"), "123\n");
        Files.writeString(group.resolve("memory.max"), "max\n");
        Files.writeString(root.resolve("memory.current"), "900000000\n");
        var sample = reader("0::/service/instance\n").read().orElseThrow();
        assertEquals(123, sample.usedBytes());
        assertNull(sample.limitBytes());
        assertTrue(reader("0::/unavailable\n").read().isEmpty());
        assertTrue(reader("0::/../../outside\n").read().isEmpty());
    }

    @Test void supportsCgroupV1AndDoesNotTreatUnlimitedSentinelAsRealLimit() throws Exception {
        Path group = Files.createDirectories(root.resolve("memory/service"));
        Files.writeString(group.resolve("memory.usage_in_bytes"), "810000000\n");
        Files.writeString(group.resolve("memory.limit_in_bytes"), "9223372036854771712\n");
        var sample = reader("7:cpu,cpuacct:/other\n8:memory:/service\n").read().orElseThrow();
        assertEquals(810_000_000L, sample.usedBytes());
        assertNull(sample.limitBytes());
        assertEquals("CGROUP_V1_ESTIMATE", sample.scope());
        Files.writeString(group.resolve("memory.limit_in_bytes"), "1073741824\n");
        assertEquals(1_073_741_824L, reader("8:memory:/service\n").read().orElseThrow().limitBytes());
    }

    @Test void missingMalformedOrNegativeUsageRemainsUnknownWhileZeroIsValid() throws Exception {
        assertTrue(reader("0::/\n").read().isEmpty());
        for (String value : new String[]{"NaN", "-1", "999999999999999999999999"}) {
            Files.writeString(root.resolve("memory.current"), value);
            assertTrue(reader("0::/\n").read().isEmpty());
        }
        Files.writeString(root.resolve("memory.current"), "0\n");
        assertEquals(0, reader("0::/\n").read().orElseThrow().usedBytes());
        assertNull(reader("0::/\n").read().orElseThrow().limitBytes());
        assertTrue(new ContainerMemoryReader(root, root.resolve("missing-membership")).read().isEmpty());
    }
}
