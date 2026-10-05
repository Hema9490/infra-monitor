package com.infra.monitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infra.monitor.config.MonitorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Sends alert text to Slack (incoming webhook) and/or email. Failures never break monitoring. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final MonitorProperties props;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public NotificationService(MonitorProperties props, ObjectProvider<JavaMailSender> mailSender, ObjectMapper mapper) {
        this.props = props;
        this.mailSender = mailSender;
        this.mapper = mapper;
    }

    public void send(String subject, String body) {
        log.warn("ALERT: {} - {}", subject, body);
        sendSlack(subject + "\n" + body);
        sendEmail(subject, body);
    }

    private void sendSlack(String text) {
        String url = props.notifications().slackWebhookUrl();
        if (url == null || url.isBlank()) return;
        try {
            String json = mapper.writeValueAsString(Map.of("text", text));
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) log.error("Slack webhook returned HTTP {}", res.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Slack notification failed: {}", e.getMessage());
        }
    }

    private void sendEmail(String subject, String body) {
        String to = props.notifications().emailTo();
        if (to == null || to.isBlank()) return;
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("ALERT_EMAIL_TO is set but no SMTP server is configured (set SPRING_MAIL_HOST).");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.notifications().emailFrom());
            msg.setTo(to.split("\\s*,\\s*"));
            msg.setSubject("[infra-monitor] " + subject);
            msg.setText(body);
            sender.send(msg);
        } catch (Exception e) {
            log.error("Email notification failed: {}", e.getMessage());
        }
    }
}
