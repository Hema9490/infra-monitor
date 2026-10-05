package com.infra.monitor.service;

import com.infra.monitor.config.MonitorProperties;
import com.infra.monitor.model.Alert;
import com.infra.monitor.model.CheckResult;
import com.infra.monitor.repo.CheckResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/** Runs the configured PING / TCP / HTTP checks on a schedule. */
@Service
public class NetworkCheckService {

    private static final Logger log = LoggerFactory.getLogger(NetworkCheckService.class);
    private static final int TIMEOUT_MS = 3000;

    private final MonitorProperties props;
    private final CheckResultRepository results;
    private final AlertService alerts;
    private final RemediationService remediation;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(TIMEOUT_MS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public NetworkCheckService(MonitorProperties props, CheckResultRepository results,
                               AlertService alerts, RemediationService remediation) {
        this.props = props;
        this.results = results;
        this.alerts = alerts;
        this.remediation = remediation;
    }

    @Scheduled(fixedDelayString = "${monitor.check-interval-ms:30000}",
               initialDelayString = "${monitor.check-initial-delay-ms:10000}")
    public void runAll() {
        for (MonitorProperties.Check check : props.checks()) {
            try {
                CheckResult result = results.save(execute(check));
                Optional<Alert> opened = alerts.evaluateCheck(check, result);
                if (!result.isUp() && check.remediation() != null) {
                    remediation.remediate(check.name(), check.remediation())
                            .ifPresent(note -> alerts.annotate("CHECK_DOWN:" + check.name(), note));
                } else if (opened.isPresent()) {
                    log.debug("Alert opened for {}", check.name());
                }
            } catch (Exception e) {
                log.error("Check '{}' crashed: {}", check.name(), e.getMessage());
            }
        }
    }

    /** Package-private so it can be unit tested. */
    CheckResult execute(MonitorProperties.Check c) {
        long start = System.nanoTime();
        boolean up = false;
        String message;
        try {
            switch (c.type()) {
                case PING -> {
                    up = InetAddress.getByName(c.host()).isReachable(TIMEOUT_MS);
                    message = up ? "reachable" : "no reply";
                }
                case TCP -> {
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(c.host(), c.port()), TIMEOUT_MS);
                    }
                    up = true;
                    message = "port open";
                }
                case HTTP -> {
                    HttpRequest req = HttpRequest.newBuilder(URI.create(c.url()))
                            .timeout(Duration.ofMillis(TIMEOUT_MS + 2000)).GET().build();
                    int status = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
                    up = status >= 200 && status < 400;
                    message = "HTTP " + status;
                }
                default -> message = "unsupported check type";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            message = "interrupted";
        } catch (Exception e) {
            message = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        return new CheckResult(c.name(), c.type(), target(c), up, ms, message);
    }

    private static String target(MonitorProperties.Check c) {
        return switch (c.type()) {
            case PING -> c.host();
            case TCP -> c.host() + ":" + c.port();
            case HTTP -> c.url();
        };
    }
}
