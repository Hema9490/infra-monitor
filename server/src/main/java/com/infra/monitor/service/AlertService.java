package com.infra.monitor.service;

import com.infra.monitor.config.MonitorProperties;
import com.infra.monitor.model.*;
import com.infra.monitor.repo.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Threshold evaluation and alert lifecycle.
 * Rule: at most one OPEN alert per alert key. It is raised when a condition starts and
 * resolved automatically when the condition clears.
 */
@Service
public class AlertService {

    private static final double CRITICAL_PERCENT = 95.0;

    private final AlertRepository alerts;
    private final NotificationService notifier;
    private final MonitorProperties props;

    public AlertService(AlertRepository alerts, NotificationService notifier, MonitorProperties props) {
        this.alerts = alerts;
        this.notifier = notifier;
        this.props = props;
    }

    @Transactional
    public void evaluate(Metric m) {
        threshold(m.getHost(), "CPU", m.getCpuPercent(), props.thresholds().cpu());
        threshold(m.getHost(), "MEMORY", m.getMemoryPercent(), props.thresholds().memory());
        threshold(m.getHost(), "DISK", m.getDiskPercent(), props.thresholds().disk());
    }

    /** Returns the alert only when it was newly opened by this call. */
    @Transactional
    public Optional<Alert> evaluateCheck(MonitorProperties.Check check, CheckResult result) {
        String key = "CHECK_DOWN:" + check.name();
        if (result.isUp()) {
            resolve(key);
            return Optional.empty();
        }
        String msg = String.format("Check '%s' (%s %s) is DOWN: %s",
                check.name(), result.getType(), result.getTarget(), result.getMessage());
        return raise(key, check.name(), Severity.CRITICAL, msg);
    }

    @Transactional
    public Optional<Alert> raise(String key, String source, Severity severity, String message) {
        if (alerts.findFirstByAlertKeyAndStatus(key, AlertStatus.OPEN).isPresent()) {
            return Optional.empty(); // already open - do not spam
        }
        Alert alert = alerts.save(new Alert(key, source, severity, message));
        notifier.send(severity + " - " + key, message);
        return Optional.of(alert);
    }

    @Transactional
    public boolean resolve(String key) {
        Optional<Alert> open = alerts.findFirstByAlertKeyAndStatus(key, AlertStatus.OPEN);
        open.ifPresent(a -> {
            a.resolve();
            alerts.save(a);
            notifier.send("RESOLVED - " + key, "Condition cleared: " + a.getMessage());
        });
        return open.isPresent();
    }

    @Transactional
    public Optional<Alert> resolveById(Long id) {
        return alerts.findById(id)
                .filter(a -> a.getStatus() == AlertStatus.OPEN)
                .map(a -> {
                    a.resolve();
                    return alerts.save(a);
                });
    }

    /** Attach the outcome of an auto-remediation attempt to the open alert. */
    @Transactional
    public void annotate(String key, String note) {
        alerts.findFirstByAlertKeyAndStatus(key, AlertStatus.OPEN).ifPresent(a -> {
            a.setRemediationNote(note);
            alerts.save(a);
        });
    }

    private void threshold(String host, String metric, double value, double limit) {
        String key = "HIGH_" + metric + ":" + host;
        if (value >= limit) {
            Severity sev = value >= CRITICAL_PERCENT ? Severity.CRITICAL : Severity.WARNING;
            String msg = String.format(Locale.ROOT, "%s usage on %s is %.1f%% (threshold %.0f%%)",
                    metric, host, value, limit);
            raise(key, host, sev, msg);
        } else {
            resolve(key);
        }
    }
}
