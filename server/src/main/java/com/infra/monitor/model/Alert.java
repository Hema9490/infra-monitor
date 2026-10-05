package com.infra.monitor.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "alerts", indexes = @Index(name = "idx_alerts_key_status", columnList = "alert_key, status"))
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable identity such as HIGH_CPU:web-1 - at most one OPEN alert exists per key. */
    @Column(name = "alert_key", nullable = false)
    private String alertKey;

    @Column(name = "source", nullable = false)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AlertStatus status;

    @Column(name = "remediation_note", length = 1000)
    private String remediationNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Alert() {}

    public Alert(String alertKey, String source, Severity severity, String message) {
        this.alertKey = alertKey;
        this.source = source;
        this.severity = severity;
        this.message = message.length() > 500 ? message.substring(0, 500) : message;
        this.status = AlertStatus.OPEN;
        this.createdAt = Instant.now();
    }

    public void resolve() {
        this.status = AlertStatus.RESOLVED;
        this.resolvedAt = Instant.now();
    }

    public void setRemediationNote(String note) {
        this.remediationNote = note == null ? null : (note.length() > 1000 ? note.substring(0, 1000) : note);
    }

    public Long getId() { return id; }
    public String getAlertKey() { return alertKey; }
    public String getSource() { return source; }
    public Severity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public AlertStatus getStatus() { return status; }
    public String getRemediationNote() { return remediationNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
