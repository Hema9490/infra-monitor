package com.infra.monitor.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "metrics", indexes = @Index(name = "idx_metrics_host_time", columnList = "host, collected_at"))
public class Metric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "host", nullable = false)
    private String host;

    @Column(name = "cpu_percent", nullable = false)
    private double cpuPercent;

    @Column(name = "memory_percent", nullable = false)
    private double memoryPercent;

    @Column(name = "disk_percent", nullable = false)
    private double diskPercent;

    @Column(name = "uptime_seconds", nullable = false)
    private long uptimeSeconds;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    protected Metric() {}

    public Metric(String host, double cpuPercent, double memoryPercent, double diskPercent, long uptimeSeconds) {
        this.host = host;
        this.cpuPercent = cpuPercent;
        this.memoryPercent = memoryPercent;
        this.diskPercent = diskPercent;
        this.uptimeSeconds = uptimeSeconds;
        this.collectedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getHost() { return host; }
    public double getCpuPercent() { return cpuPercent; }
    public double getMemoryPercent() { return memoryPercent; }
    public double getDiskPercent() { return diskPercent; }
    public long getUptimeSeconds() { return uptimeSeconds; }
    public Instant getCollectedAt() { return collectedAt; }
}
