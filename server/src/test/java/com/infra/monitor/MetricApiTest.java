package com.infra.monitor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MetricApiTest {

    @Autowired MockMvc mvc;

    private static String body(String host, double cpu) {
        return String.format(java.util.Locale.ROOT,
                "{\"host\":\"%s\",\"cpuPercent\":%.1f,\"memoryPercent\":40.0,\"diskPercent\":50.0,\"uptimeSeconds\":120}",
                host, cpu);
    }

    @Test
    void highCpuOpensAlertAndRecoveryResolvesIt() throws Exception {
        String key = "HIGH_CPU:cpu-test-host";

        mvc.perform(post("/api/metrics").contentType(MediaType.APPLICATION_JSON).content(body("cpu-test-host", 97)))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/alerts").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.alertKey=='" + key + "')]").exists());

        mvc.perform(post("/api/metrics").contentType(MediaType.APPLICATION_JSON).content(body("cpu-test-host", 10)))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/alerts").param("status", "OPEN"))
                .andExpect(jsonPath("$[?(@.alertKey=='" + key + "')]").isEmpty());
    }

    @Test
    void duplicateBreachDoesNotCreateSecondOpenAlert() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/metrics").contentType(MediaType.APPLICATION_JSON).content(body("dup-host", 99)))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(get("/api/alerts").param("status", "OPEN"))
                .andExpect(jsonPath("$[?(@.alertKey=='HIGH_CPU:dup-host')]", hasSize(1)));
    }

    @Test
    void invalidPayloadIsRejected() throws Exception {
        mvc.perform(post("/api/metrics").contentType(MediaType.APPLICATION_JSON).content(body("bad-host", 250)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/metrics").contentType(MediaType.APPLICATION_JSON).content(body(" ", 10)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hostsEndpointReturnsLatestMetric() throws Exception {
        mvc.perform(post("/api/metrics").contentType(MediaType.APPLICATION_JSON).content(body("hosts-test-host", 20)))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/hosts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.host=='hosts-test-host')]").exists());
    }
}
