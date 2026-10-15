package com.ybc.ybc.global.monitoring.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads the current process's cgroup accounting; never substitutes host RAM or JVM heap. */
@Component
public class ContainerMemoryReader {
    private final Path root;
    private final Path membership;

    public ContainerMemoryReader() { this(Path.of("/sys/fs/cgroup"), Path.of("/proc/self/cgroup")); }
    ContainerMemoryReader(Path root, Path membership) { this.root = root; this.membership = membership; }

    public Optional<Sample> read() {
        List<Candidate> candidates = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(membership)) {
                String[] fields = line.split(":", 3);
                if (fields.length != 3) continue;
                if (fields[0].equals("0") && fields[1].isEmpty()) {
                    Path directory = within(root, fields[2]);
                    if (directory != null) candidates.add(new Candidate(directory, true));
                } else if (List.of(fields[1].split(",")).contains("memory")) {
                    Path directory = within(root.resolve("memory"), fields[2]);
                    if (directory != null) candidates.add(new Candidate(directory, false));
                    if (fields[2].equals("/")) candidates.add(new Candidate(root, false));
                }
            }
        } catch (IOException | RuntimeException unavailable) { return Optional.empty(); }
        // Never read a parent/root group's counters when the current process belongs to a different group.
        for (Candidate candidate : candidates) {
            try {
                Path usage = candidate.directory().resolve(candidate.v2() ? "memory.current" : "memory.usage_in_bytes");
                long used = Long.parseLong(Files.readString(usage).strip());
                if (used < 0) continue;
                Long limit = limit(candidate.directory().resolve(candidate.v2() ? "memory.max" : "memory.limit_in_bytes"));
                return Optional.of(new Sample(used, limit, candidate.v2() ? "CGROUP_V2" : "CGROUP_V1_ESTIMATE"));
            } catch (IOException | RuntimeException unavailable) { /* Unknown stays unknown, including malformed counters. */ }
        }
        return Optional.empty();
    }

    private static Path within(Path base, String relative) {
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path candidate = normalizedBase.resolve(relative.replaceFirst("^/", "")).normalize();
        return candidate.startsWith(normalizedBase) ? candidate : null;
    }

    private static Long limit(Path file) {
        try {
            String value = Files.readString(file).strip();
            if (value.equals("max")) return null;
            long parsed = Long.parseLong(value);
            // cgroup v1 represents an unlimited allocation with a page-aligned near-LONG_MAX sentinel.
            return parsed > 0 && parsed < Long.MAX_VALUE / 2 ? parsed : null;
        } catch (IOException | RuntimeException unavailable) { return null; }
    }

    private record Candidate(Path directory, boolean v2) { }
    public record Sample(long usedBytes, Long limitBytes, String scope) { }
}
