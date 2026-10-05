package com.infra.monitor.service;

import com.infra.monitor.config.MonitorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Runs an operator-configured remediation script when a check fails.
 * Safety rules:
 *  - disabled unless monitor.remediation.enabled=true
 *  - the command comes only from application.yml, never from an API request
 *  - the script must live inside monitor.remediation.scripts-dir
 *  - no shell is used and every token must match a strict allow-list pattern
 *  - a per-check cooldown prevents restart loops
 */
@Service
public class RemediationService {

    private static final Logger log = LoggerFactory.getLogger(RemediationService.class);
    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9_.@-]+");
    private static final int TIMEOUT_SECONDS = 60;

    private final MonitorProperties props;
    private final Map<String, Instant> lastRun = new ConcurrentHashMap<>();

    public RemediationService(MonitorProperties props) {
        this.props = props;
    }

    /** Returns a human-readable outcome, or empty when nothing was attempted. */
    public Optional<String> remediate(String checkName, String commandLine) {
        MonitorProperties.Remediation cfg = props.remediation();
        if (!cfg.enabled() || commandLine == null || commandLine.isBlank()) {
            return Optional.empty();
        }

        String[] tokens = commandLine.trim().split("\\s+");
        for (String t : tokens) {
            if (!SAFE_TOKEN.matcher(t).matches() || t.contains("..")) {
                return Optional.of("Remediation rejected: unsafe token '" + t + "'");
            }
        }

        Instant last = lastRun.get(checkName);
        if (last != null && last.plusSeconds(cfg.cooldownSeconds()).isAfter(Instant.now())) {
            return Optional.empty(); // still cooling down
        }

        Path dir = Path.of(cfg.scriptsDir()).toAbsolutePath().normalize();
        Path script = dir.resolve(tokens[0]).normalize();
        if (!script.startsWith(dir) || !Files.isExecutable(script)) {
            return Optional.of("Remediation skipped: script '" + tokens[0] + "' not found or not executable in " + dir);
        }

        lastRun.put(checkName, Instant.now());
        List<String> cmd = new ArrayList<>();
        cmd.add(script.toString());
        for (int i = 1; i < tokens.length; i++) cmd.add(tokens[i]);

        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return Optional.of("Remediation '" + commandLine + "' timed out after " + TIMEOUT_SECONDS + "s");
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String result = "Ran '" + commandLine + "' at " + Instant.now() + " exit=" + p.exitValue()
                    + (out.isEmpty() ? "" : " output=" + out);
            log.info("Remediation for {}: {}", checkName, result);
            return Optional.of(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.of("Remediation interrupted");
        } catch (IOException e) {
            return Optional.of("Remediation failed to start: " + e.getMessage());
        }
    }
}
