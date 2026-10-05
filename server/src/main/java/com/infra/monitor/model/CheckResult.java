package com.infra.monitor.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "check_results", indexes = @Index(name = "idx_checks_name_time", columnList = "name, checked_at"))
public class CheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false)
    private CheckType type;

    @Column(name = "target")
    private String target;

    @Column(name = "is_up", nullable = false)
    private boolean up;

    @Column(name = "response_ms", nullable = false)
    private long responseMs;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    protected CheckResult() {}

    public CheckResult(String name, CheckType type, String target, boolean up, long responseMs, String message) {
        this.name = name;
        this.type = type;
        this.target = target;
        this.up = up;
        this.responseMs = responseMs;
        this.message = message == null ? "" : (message.length() > 500 ? message.substring(0, 500) : message);
        this.checkedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public CheckType getType() { return type; }
    public String getTarget() { return target; }
    public boolean isUp() { return up; }
    public long getResponseMs() { return responseMs; }
    public String getMessage() { return message; }
    public Instant getCheckedAt() { return checkedAt; }
}
