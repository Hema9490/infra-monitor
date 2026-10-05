package com.infra.monitor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "monitor.security.api-key=s3cret")
@AutoConfigureMockMvc
class ApiKeyTest {

    @Autowired MockMvc mvc;

    private static final String BODY =
            "{\"host\":\"key-test-host\",\"cpuPercent\":5,\"memoryPercent\":5,\"diskPercent\":5,\"uptimeSeconds\":1}";

    @Test
    void rejectsMissingOrWrongKey() throws Exception {
        mvc.perform(post("/api/metrics").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/alerts").header("X-API-Key", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsCorrectKey() throws Exception {
        mvc.perform(post("/api/metrics").header("X-API-Key", "s3cret")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted());
    }
}
