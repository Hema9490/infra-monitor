package com.infra.monitor;

import com.infra.monitor.config.MonitorProperties;
import com.infra.monitor.service.RemediationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemediationServiceTest {

    private static MonitorProperties props(boolean enabled) {
        return new MonitorProperties(
                new MonitorProperties.Security(""),
                new MonitorProperties.Thresholds(85, 90, 85),
                new MonitorProperties.Notify("", "", "monitor@localhost"),
                new MonitorProperties.Remediation(enabled, "./scripts", 300),
                14, 60, List.of());
    }

    @Test
    void doesNothingWhenDisabled() {
        assertEquals(java.util.Optional.empty(), new RemediationService(props(false)).remediate("web", "restart-service.sh web"));
    }

    @Test
    void rejectsPathTraversal() {
        String result = new RemediationService(props(true)).remediate("web", "../evil.sh").orElseThrow();
        assertTrue(result.startsWith("Remediation rejected"), result);
    }

    @Test
    void rejectsShellMetacharacters() {
        String result = new RemediationService(props(true)).remediate("web", "restart-service.sh web;rm").orElseThrow();
        assertTrue(result.startsWith("Remediation rejected"), result);
    }

    @Test
    void skipsUnknownScript() {
        String result = new RemediationService(props(true)).remediate("web", "does-not-exist.sh").orElseThrow();
        assertTrue(result.startsWith("Remediation skipped"), result);
    }
}
