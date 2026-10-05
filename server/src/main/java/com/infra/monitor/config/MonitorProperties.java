package com.infra.monitor.config;

import com.infra.monitor.model.CheckType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/** All tunables live under the "monitor" prefix in application.yml. */
@ConfigurationProperties(prefix = "monitor")
public record MonitorProperties(
        @DefaultValue Security security,
        @DefaultValue Thresholds thresholds,
        @DefaultValue Notify notifications,
        @DefaultValue Remediation remediation,
        @DefaultValue("14") int retentionDays,
        @DefaultValue("60") int staleHostSeconds,
        List<Check> checks) {

    public MonitorProperties {
        checks = checks == null ? List.of() : checks;
    }

    /** If apiKey is non-blank, every /api/** request must send it in the X-API-Key header. */
    public record Security(@DefaultValue("") String apiKey) {}

    /** Percent values that trigger an alert. */
    public record Thresholds(@DefaultValue("85") double cpu,
                             @DefaultValue("90") double memory,
                             @DefaultValue("85") double disk) {}

    public record Notify(@DefaultValue("") String slackWebhookUrl,
                         @DefaultValue("") String emailTo,
                         @DefaultValue("monitor@localhost") String emailFrom) {}

    /** Auto-remediation runs allow-listed scripts from scriptsDir only. */
    public record Remediation(@DefaultValue("false") boolean enabled,
                              @DefaultValue("./scripts") String scriptsDir,
                              @DefaultValue("300") int cooldownSeconds) {}

    /** One active check. Use host (+port) for PING/TCP and url for HTTP. */
    public record Check(String name, CheckType type, String host, Integer port, String url, String remediation) {}
}
