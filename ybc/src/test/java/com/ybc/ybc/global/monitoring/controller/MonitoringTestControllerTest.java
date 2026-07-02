package com.ybc.ybc.global.monitoring.controller;

import com.ybc.ybc.global.monitoring.config.MonitoringProperties;
import com.ybc.ybc.global.monitoring.service.ErrorAlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MonitoringTestControllerTest {

    @Mock
    private ErrorAlertService errorAlertService;

    @Test
    void sendTestAlertReturnsNotFoundWhenFeatureIsDisabled() {
        MonitoringTestController controller = new MonitoringTestController(
                errorAlertService,
                monitoringProperties(false, "secret-token")
        );
        MockHttpServletRequest request = request();

        ResponseEntity<Void> response = controller.sendTestAlert("secret-token", request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(errorAlertService, never()).sendManualTestAlert(any(), anyString());
    }

    @Test
    void sendTestAlertReturnsForbiddenWhenTokenDoesNotMatch() {
        MonitoringTestController controller = new MonitoringTestController(
                errorAlertService,
                monitoringProperties(true, "secret-token")
        );
        MockHttpServletRequest request = request();

        ResponseEntity<Void> response = controller.sendTestAlert("wrong-token", request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(errorAlertService, never()).sendManualTestAlert(any(), anyString());
    }

    @Test
    void sendTestAlertDispatchesDiscordAlertWhenTokenMatches() {
        MonitoringTestController controller = new MonitoringTestController(
                errorAlertService,
                monitoringProperties(true, "secret-token")
        );
        MockHttpServletRequest request = request();

        ResponseEntity<Void> response = controller.sendTestAlert("secret-token", request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(errorAlertService).sendManualTestAlert(request, MonitoringTestController.MANUAL_TRIGGER_SOURCE);
    }

    private MonitoringProperties monitoringProperties(boolean enabled, String token) {
        return new MonitoringProperties(
                null,
                null,
                new MonitoringProperties.TestEndpoint(enabled, token)
        );
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("POST", MonitoringTestController.TEST_ALERT_PATH);
    }
}
