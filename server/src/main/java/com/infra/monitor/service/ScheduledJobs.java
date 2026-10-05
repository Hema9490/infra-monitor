package com.infra.monitor.service;

import com.infra.monitor.config.MonitorProperties;
import com.infra.monitor.model.Metric;
import com.infra.monitor.model.Severity;
import com.infra.monitor.repo.CheckResultRepository;
import com.infra.monitor.repo.MetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Housekeeping: detect silent hosts, purge old data. */
@Component
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final MetricRepository metrics;
    private final CheckResultRepository checks;
    private final AlertService alerts;
    private final MonitorProperties props;

    public ScheduledJobs(MetricRepository metrics, CheckResultRepository checks,
                         AlertService alerts, MonitorProperties props) {
        this.metrics = metrics;
        this.checks = checks;
        this.alerts = alerts;
        this.props = props;
    }

    /** A host that stops reporting is treated as down. */
    @Scheduled(fixedDelay = 15_000, initialDelay = 30_000)
    public void detectStaleHosts() {
        Instant now = Instant.now();
        for (Metric latest : metrics.findLatestPerHost()) {
            long age = Duration.between(latest.getCollectedAt(), now).getSeconds();
            String key = "HOST_DOWN:" + latest.getHost();
            if (age > props.staleHostSeconds()) {
                alerts.raise(key, latest.getHost(), Severity.CRITICAL,
                        "No metrics received from " + latest.getHost() + " for " + age + "s");
            } else {
                alerts.resolve(key);
            }
        }
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeOldData() {
        Instant cutoff = Instant.now().minus(props.retentionDays(), ChronoUnit.DAYS);
        int m = metrics.deleteOlderThan(cutoff);
        int c = checks.deleteOlderThan(cutoff);
        log.info("Retention purge removed {} metrics and {} check results older than {}", m, c, cutoff);
    }
}
