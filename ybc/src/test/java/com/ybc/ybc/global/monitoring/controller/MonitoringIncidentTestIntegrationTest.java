package com.ybc.ybc.global.monitoring.controller;

import com.ybc.ybc.global.monitoring.incident.IncidentAlertTrigger;
import com.ybc.ybc.global.monitoring.incident.IncidentTestResult;
import com.ybc.ybc.support.TestMailConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.ybc.ybc.global.monitoring.controller.MonitoringTestController.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.monitoring.test-endpoint.enabled=true",
        "app.monitoring.test-endpoint.token=test-secret",
        "app.monitoring.discord.enabled=false",
        "app.monitoring.memory.enabled=false",
        "app.monitoring.operational.enabled=false"
})
@AutoConfigureMockMvc
@Import(TestMailConfig.class)
class MonitoringIncidentTestIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean IncidentAlertTrigger trigger;

    @Test void requiresTheDedicatedTokenEvenWithoutJwt() throws Exception {
        mvc.perform(post(TEST_INCIDENT_PATH)).andExpect(status().isForbidden());
        mvc.perform(post(TEST_INCIDENT_PATH).header(TEST_ALERT_TOKEN_HEADER, "wrong"))
                .andExpect(status().isForbidden());
        verify(trigger, never()).testAdminAccessDenied(anyString(), anyString());
        mvc.perform(get("/members")).andExpect(status().isUnauthorized());
        mvc.perform(post(TEST_INCIDENT_PATH + "/extra").header(TEST_ALERT_TOKEN_HEADER, "test-secret"))
                .andExpect(status().isUnauthorized());
    }

    @Test void returnsSuccessOnlyAfterConfirmedDeliveryAndUsesOnlyFictionalEvidence() throws Exception {
        when(trigger.testAdminAccessDenied(anyString(), anyString())).thenAnswer(call -> {
            assertThat(IncidentAlertTrigger.isRequestActive()).isTrue();
            String evidence = call.getArgument(1);
            assertThat(evidence).contains(call.getArgument(0, String.class), "SIMULATION", "synthetic-member-001", "Status: 403");
            assertThat(evidence).doesNotContain("test-secret", "private-query", "sensitive-body");
            return new IncidentTestResult(IncidentTestResult.Status.DELIVERED, "DISCORD_DELIVERY", "DISCORD_MESSAGE_CONFIRMED", 0);
        });
        mvc.perform(post(TEST_INCIDENT_PATH).queryParam("private-query", "private-query")
                        .content("sensitive-body").header(TEST_ALERT_TOKEN_HEADER, "test-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testId").isNotEmpty())
                .andExpect(jsonPath("$.scenario").value("ADMIN_ACCESS_DENIED"))
                .andExpect(jsonPath("$.simulation").value(true))
                .andExpect(jsonPath("$.result.status").value("DELIVERED"));
        assertThat(IncidentAlertTrigger.isRequestActive()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"DISABLED,503", "NOT_CONFIGURED,503", "BUSY,503", "FAILED,502", "COOLDOWN,429"})
    void exposesOutcomeInsteadOfClaimingAcceptedMeansDelivered(IncidentTestResult.Status outcome, int httpStatus) throws Exception {
        when(trigger.testAdminAccessDenied(anyString(), anyString()))
                .thenReturn(new IncidentTestResult(outcome, "TEST_STAGE", "SAFE_REASON", outcome == IncidentTestResult.Status.COOLDOWN ? 300 : 0));
        var result = mvc.perform(post(TEST_INCIDENT_PATH).header(TEST_ALERT_TOKEN_HEADER, "test-secret"))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.result.status").value(outcome.name()))
                .andExpect(jsonPath("$.result.stage").value("TEST_STAGE"))
                .andExpect(jsonPath("$.result.reason").value("SAFE_REASON"));
        if (outcome == IncidentTestResult.Status.COOLDOWN) result.andExpect(header().string("Retry-After", "300"));
    }
}
