package com.infra.monitor.web;

import com.infra.monitor.model.*;
import com.infra.monitor.repo.AlertRepository;
import com.infra.monitor.repo.CheckResultRepository;
import com.infra.monitor.repo.MetricRepository;
import com.infra.monitor.service.AlertService;
import com.infra.monitor.service.MetricService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final MetricService metricService;
    private final MetricRepository metrics;
    private final CheckResultRepository checks;
    private final AlertRepository alerts;
    private final AlertService alertService;

    public ApiController(MetricService metricService, MetricRepository metrics, CheckResultRepository checks,
                         AlertRepository alerts, AlertService alertService) {
        this.metricService = metricService;
        this.metrics = metrics;
        this.checks = checks;
        this.alerts = alerts;
        this.alertService = alertService;
    }

    /** Agents push metrics here. */
    @PostMapping("/metrics")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Metric ingest(@Valid @RequestBody MetricRequest request) {
        return metricService.ingest(request);
    }

    /** Recent metrics, newest first. Optional host filter. limit is capped at 1000. */
    @GetMapping("/metrics")
    public List<Metric> recent(@RequestParam(required = false) String host,
                               @RequestParam(defaultValue = "100") int limit) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 1000));
        return host == null || host.isBlank()
                ? metrics.findAllByOrderByCollectedAtDesc(page)
                : metrics.findByHostOrderByCollectedAtDesc(host, page);
    }

    /** Latest metric per host. */
    @GetMapping("/hosts")
    public List<Metric> hosts() {
        return metrics.findLatestPerHost();
    }

    /** Latest result per configured check. */
    @GetMapping("/checks")
    public List<CheckResult> checks() {
        return checks.findLatestPerCheck();
    }

    /** Alerts, newest first. Use ?status=OPEN or RESOLVED to filter. */
    @GetMapping("/alerts")
    public List<Alert> alerts(@RequestParam(required = false) AlertStatus status) {
        return status == null
                ? alerts.findTop100ByOrderByCreatedAtDesc()
                : alerts.findByStatusOrderByCreatedAtDesc(status);
    }

    @PostMapping("/alerts/{id}/resolve")
    public ResponseEntity<Alert> resolve(@PathVariable Long id) {
        return alertService.resolveById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
