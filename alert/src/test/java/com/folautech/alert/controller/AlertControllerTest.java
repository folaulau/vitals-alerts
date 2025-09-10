package com.folautech.alert.controller;

import com.folautech.alert.model.*;
import com.folautech.alert.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(AlertController.class)
@ActiveProfiles("test")
class AlertControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AlertService alertService;

    private String patientId;
    private String readingId;
    private String capturedAt;

    @BeforeEach
    void setUp() {
        patientId = "p-001";
        readingId = "test-reading-1";
        capturedAt = LocalDateTime.now().toString();
    }

    @Test
    @DisplayName("Should accept BP reading for evaluation")
    void testEvaluateBPReading() {
        when(alertService.evaluateReadings(anyList())).thenReturn(Flux.empty());

        String requestBody = """
            [{
                "type": "BP",
                "readingId": "%s",
                "patientId": "%s",
                "systolic": 150,
                "diastolic": 95,
                "capturedAt": "%s"
            }]
            """.formatted(readingId, patientId, capturedAt);

        webTestClient.post()
                .uri("/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Should accept HR reading for evaluation")
    void testEvaluateHRReading() {
        when(alertService.evaluateReadings(anyList())).thenReturn(Flux.empty());

        String requestBody = """
            [{
                "type": "HR",
                "readingId": "%s",
                "patientId": "%s",
                "hr": 45,
                "capturedAt": "%s"
            }]
            """.formatted(readingId, patientId, capturedAt);

        webTestClient.post()
                .uri("/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Should accept SPO2 reading for evaluation")
    void testEvaluateSPO2Reading() {
        when(alertService.evaluateReadings(anyList())).thenReturn(Flux.empty());

        String requestBody = """
            [{
                "type": "SPO2",
                "readingId": "%s",
                "patientId": "%s",
                "spo2": 88,
                "capturedAt": "%s"
            }]
            """.formatted(readingId, patientId, capturedAt);

        webTestClient.post()
                .uri("/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Should return 500 when service throws error")
    void testEvaluateReadingError() {
        when(alertService.evaluateReadings(anyList()))
                .thenReturn(Flux.error(new RuntimeException("Service error")));

        String requestBody = """
            [{
                "type": "BP",
                "readingId": "%s",
                "patientId": "%s",
                "systolic": 150,
                "diastolic": 95,
                "capturedAt": "%s"
            }]
            """.formatted(readingId, patientId, capturedAt);

        webTestClient.post()
                .uri("/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    @DisplayName("Should return alerts for patient")
    void testGetAlerts() {
        Alert alert1 = new Alert("alert-1", patientId, "reading-1", "BP", 
                AlertType.HIGH, "Systolic >= 140", "145/85", LocalDateTime.now().minusHours(2));
        alert1.setId(1L);
        alert1.setCreatedAt(LocalDateTime.now());
        
        Alert alert2 = new Alert("alert-2", patientId, "reading-2", "HR", 
                AlertType.LOW, "Heart Rate < 50", "45", LocalDateTime.now().minusHours(1));
        alert2.setId(2L);
        alert2.setCreatedAt(LocalDateTime.now());

        when(alertService.getAlertsByPatientId(patientId))
                .thenReturn(Flux.just(alert2, alert1));

        webTestClient.get()
                .uri("/alerts?patientId=" + patientId)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Alert.class)
                .hasSize(2);
    }

    @Test
    @DisplayName("Should return empty list when no alerts exist")
    void testGetAlertsEmpty() {
        when(alertService.getAlertsByPatientId(patientId))
                .thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/alerts?patientId=" + patientId)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Alert.class)
                .hasSize(0);
    }

    @Test
    @DisplayName("Should handle missing patient ID parameter")
    void testGetAlertsMissingPatientId() {
        webTestClient.get()
                .uri("/alerts")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Should handle empty patient ID parameter")
    void testGetAlertsEmptyPatientId() {
        webTestClient.get()
                .uri("/alerts?patientId=")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    @DisplayName("Health check should return OK")
    void testHealthCheck() {
        webTestClient.get()
                .uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Alert Service is running");
    }
}