package com.recommendation.intelligentoutfitrecommendationsystem.observability;

import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObservabilityEndpointsTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationMetrics applicationMetrics;

    @Test
    void livenessProbeIsPublicAndIndependentFromExternalInfrastructure() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessProbeIncludesDatabaseButNotRedis() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.redis").doesNotExist());
    }

    @Test
    void prometheusEndpointIsAvailableForInfrastructureScraping() throws Exception {
        applicationMetrics.recordPaymentCallback("success");

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("# HELP")))
                .andExpect(content().string(containsString("app_payment_callbacks_total")));
    }

    @Test
    void infoEndpointPublishesBuildVersionAndDeploymentCommitSlot() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.version").value("0.0.1-SNAPSHOT"))
                .andExpect(jsonPath("$.app.commit").isNotEmpty());
    }

    @Test
    void sensitiveActuatorEndpointsAreNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env").with(jwt()))
                .andExpect(status().isNotFound());
    }
}
