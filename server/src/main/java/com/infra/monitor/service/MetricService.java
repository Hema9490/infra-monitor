package com.infra.monitor.service;

import com.infra.monitor.model.Metric;
import com.infra.monitor.repo.MetricRepository;
import com.infra.monitor.web.MetricRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricService {

    private final MetricRepository metrics;
    private final AlertService alerts;

    public MetricService(MetricRepository metrics, AlertService alerts) {
        this.metrics = metrics;
        this.alerts = alerts;
    }

    @Transactional
    public Metric ingest(MetricRequest r) {
        Metric m = metrics.save(new Metric(r.host().trim(), r.cpuPercent(), r.memoryPercent(),
                r.diskPercent(), r.uptimeSeconds()));
        alerts.evaluate(m);
        return m;
    }
}
