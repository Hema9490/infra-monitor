package com.infra.agent;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Collects CPU, memory, disk and uptime for the machine the agent runs on. */
public class MetricsCollector {

    /** Immutable snapshot of one collection cycle. */
    public record Snapshot(double cpuPercent, double memoryPercent, double diskPercent, long uptimeSeconds) {}

    private final com.sun.management.OperatingSystemMXBean os =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private final File diskPath;

    public MetricsCollector(String diskPath) {
        this.diskPath = new File(diskPath);
    }

    public Snapshot collect() {
        return new Snapshot(cpu(), memory(), disk(), uptime());
    }

    private double cpu() {
        double load = os.getCpuLoad() * 100.0; // negative when not yet available
        return clamp(load);
    }

    /** Prefers MemAvailable from /proc/meminfo (accurate on Linux); falls back to the JMX bean. */
    private double memory() {
        try {
            Path meminfo = Path.of("/proc/meminfo");
            if (Files.isReadable(meminfo)) {
                long total = -1, available = -1;
                List<String> lines = Files.readAllLines(meminfo);
                for (String line : lines) {
                    if (line.startsWith("MemTotal:")) total = kb(line);
                    else if (line.startsWith("MemAvailable:")) available = kb(line);
                }
                if (total > 0 && available >= 0) {
                    return clamp((total - available) * 100.0 / total);
                }
            }
        } catch (Exception ignored) {
            // fall through to JMX
        }
        long total = os.getTotalMemorySize();
        long free = os.getFreeMemorySize();
        return total <= 0 ? 0 : clamp((total - free) * 100.0 / total);
    }

    private double disk() {
        long total = diskPath.getTotalSpace();
        long usable = diskPath.getUsableSpace();
        return total <= 0 ? 0 : clamp((total - usable) * 100.0 / total);
    }

    private long uptime() {
        try {
            Path p = Path.of("/proc/uptime");
            if (Files.isReadable(p)) {
                return (long) Double.parseDouble(Files.readString(p).trim().split("\\s+")[0]);
            }
        } catch (Exception ignored) {
            // fall through to JVM uptime
        }
        return ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
    }

    private static long kb(String line) {
        return Long.parseLong(line.replaceAll("[^0-9]", ""));
    }

    private static double clamp(double v) {
        if (Double.isNaN(v) || v < 0) return 0;
        return Math.min(v, 100);
    }
}
