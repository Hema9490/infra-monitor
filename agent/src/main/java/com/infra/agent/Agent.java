package com.infra.agent;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Entry point. Configuration comes from environment variables:
 *   MONITOR_SERVER_URL     (default http://localhost:8080)
 *   AGENT_HOST_NAME        (default: machine hostname)
 *   AGENT_INTERVAL_SECONDS (default 15)
 *   AGENT_API_KEY          (optional, sent as X-API-Key)
 *   AGENT_DISK_PATH        (default "/" or "C:\" on Windows)
 */
public class Agent {

    public static void main(String[] args) throws Exception {
        String serverUrl = env("MONITOR_SERVER_URL", "http://localhost:8080").replaceAll("/+$", "");
        String host = env("AGENT_HOST_NAME", InetAddress.getLocalHost().getHostName());
        int interval = Integer.parseInt(env("AGENT_INTERVAL_SECONDS", "15"));
        String apiKey = env("AGENT_API_KEY", "");
        String defaultDisk = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows") ? "C:\\" : "/";
        String diskPath = env("AGENT_DISK_PATH", defaultDisk);

        MetricsCollector collector = new MetricsCollector(diskPath);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        log("Agent started: host=" + host + " server=" + serverUrl + " interval=" + interval + "s");
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdownNow));

        scheduler.scheduleAtFixedRate(() -> {
            try {
                send(client, serverUrl, apiKey, host, collector.collect());
            } catch (Exception e) {
                log("Failed to send metrics: " + e.getMessage()); // keep running; server may be restarting
            }
        }, 0, interval, TimeUnit.SECONDS);
    }

    private static void send(HttpClient client, String serverUrl, String apiKey, String host,
                             MetricsCollector.Snapshot s) throws Exception {
        String json = String.format(Locale.ROOT,
                "{\"host\":\"%s\",\"cpuPercent\":%.2f,\"memoryPercent\":%.2f,\"diskPercent\":%.2f,\"uptimeSeconds\":%d}",
                escape(host), s.cpuPercent(), s.memoryPercent(), s.diskPercent(), s.uptimeSeconds());

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(serverUrl + "/api/metrics"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (!apiKey.isBlank()) req.header("X-API-Key", apiKey);

        HttpResponse<String> res = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) {
            log("Server rejected metrics: HTTP " + res.statusCode() + " " + res.body());
        } else {
            log(String.format(Locale.ROOT, "Sent cpu=%.1f%% mem=%.1f%% disk=%.1f%%",
                    s.cpuPercent(), s.memoryPercent(), s.diskPercent()));
        }
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void log(String msg) {
        System.out.println(Instant.now() + " [agent] " + msg);
    }
}
