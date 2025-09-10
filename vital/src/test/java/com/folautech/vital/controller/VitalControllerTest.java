package com.folautech.vital.controller;

import com.folautech.vital.model.*;
import com.folautech.vital.service.VitalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@WebFluxTest(VitalController.class)
@ActiveProfiles("test")
public class VitalControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private VitalService vitalService;

    @BeforeEach
    void setUp() {
        reset(vitalService);
    }

    @Test
    @DisplayName("Should accept valid BP reading and return 200 with empty alerts")
    void testValidBPReading() {
        // Given - BP reading within normal range, no alerts
        List<Alert> emptyAlerts = new ArrayList<>();
        when(vitalService.processReadings(anyList()))
            .thenReturn(Mono.just(emptyAlerts));

        String requestBody = """
            [{
                "readingId": "test-bp-1",
                "patientId": "p-001",
                "type": "BP",
                "systolic": 120,
                "diastolic": 80,
                "capturedAt": "2025-08-01T12:00:00Z"
            }]
            """;

        // When & Then
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(0);

        verify(vitalService, times(1)).processReadings(anyList());
    }

    @Test
    @DisplayName("Should accept valid HR reading and return 200 with empty alerts")
    void testValidHRReading() {
        // Given - HR reading within normal range, no alerts
        List<Alert> emptyAlerts = new ArrayList<>();
        when(vitalService.processReadings(anyList()))
            .thenReturn(Mono.just(emptyAlerts));

        String requestBody = """
            [{
                "readingId": "test-hr-1",
                "patientId": "p-001",
                "type": "HR",
                "hr": 75,
                "capturedAt": "2025-08-01T12:00:00Z"
            }]
            """;

        // When & Then
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(0);

        verify(vitalService, times(1)).processReadings(anyList());
    }

    @Test
    @DisplayName("Should accept valid SPO2 reading and return 200 with empty alerts")
    void testValidSPO2Reading() {
        // Given - SPO2 reading within normal range, no alerts
        List<Alert> emptyAlerts = new ArrayList<>();
        when(vitalService.processReadings(anyList()))
            .thenReturn(Mono.just(emptyAlerts));

        String requestBody = """
            [{
                "readingId": "test-spo2-1",
                "patientId": "p-001",
                "type": "SPO2",
                "spo2": 98,
                "capturedAt": "2025-08-01T12:00:00Z"
            }]
            """;

        // When & Then
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(0);

        verify(vitalService, times(1)).processReadings(anyList());
    }

    @Test
    @DisplayName("Should handle duplicate readings gracefully")
    void testIdempotentReading() {
        // Given - first call succeeds, second call indicates duplicate
        when(vitalService.processReadings(anyList()))
            .thenReturn(Mono.error(new RuntimeException("Reading already exists")));

        String requestBody = """
            [{
                "readingId": "duplicate-1",
                "patientId": "p-001",
                "type": "BP",
                "systolic": 120,
                "diastolic": 80,
                "capturedAt": "2025-08-01T12:00:00Z"
            }]
            """;

        // When & Then - Should return 200 OK with empty alerts for duplicate (idempotent response)
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("Should return 400 for validation error")
    void testValidationError() {
        // Given
        when(vitalService.processReadings(anyList()))
            .thenReturn(Mono.error(new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, 
                "Validation failed")));

        String requestBody = """
            [{
                "readingId": "invalid-1",
                "patientId": "p-001",
                "type": "BP",
                "systolic": 120,
                "capturedAt": "2025-08-01T12:00:00Z"
            }]
            """;

        // When & Then
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Should handle missing required fields")
    void testMissingRequiredFields() {
        // Given
        when(vitalService.processReadings(anyList()))
            .thenReturn(Mono.error(new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, 
                "readingId is required")));

        String requestBody = """
            [{
                "patientId": "p-001",
                "type": "BP",
                "systolic": 120,
                "diastolic": 80,
                "capturedAt": "2025-08-01T12:00:00Z"
            }]
            """;

        // When & Then
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Should handle invalid JSON")
    void testInvalidJson() {
        String invalidJson = """
            [{
                "readingId": "test-1",
                "patientId": "p-001",
                invalid json here
            }]
            """;

        // When & Then
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidJson)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Should process all mock data readings as batch and return alerts")
    void testMockDataReadings() {
        // Given - Mock data should trigger 4 alerts
        List<Alert> alerts = new ArrayList<>();
        alerts.add(createAlert("11111111-1111-1111-1111-111111111111", "BP", "CRITICAL", "Systolic >= 140 AND Diastolic >= 90", "150/95"));
        alerts.add(createAlert("22222222-2222-2222-2222-222222222222", "HR", "HIGH", "Heart Rate > 110", "120"));
        alerts.add(createAlert("33333333-3333-3333-3333-333333333333", "HR", "LOW", "Heart Rate < 50", "45"));
        alerts.add(createAlert("44444444-4444-4444-4444-444444444444", "SPO2", "LOW", "SpO2 < 92", "90"));
        
        when(vitalService.processReadings(anyList()))
            .thenReturn(Mono.just(alerts));

        String batchReadings = """
            [
                {
                    "readingId": "11111111-1111-1111-1111-111111111111",
                    "patientId": "p-001",
                    "type": "BP",
                    "systolic": 150,
                    "diastolic": 95,
                    "capturedAt": "2025-08-01T12:00:00Z"
                },
                {
                    "readingId": "22222222-2222-2222-2222-222222222222",
                    "patientId": "p-001",
                    "type": "HR",
                    "hr": 120,
                    "capturedAt": "2025-08-01T12:05:00Z"
                },
                {
                    "readingId": "33333333-3333-3333-3333-333333333333",
                    "patientId": "p-001",
                    "type": "HR",
                    "hr": 45,
                    "capturedAt": "2025-08-01T12:10:00Z"
                },
                {
                    "readingId": "44444444-4444-4444-4444-444444444444",
                    "patientId": "p-001",
                    "type": "SPO2",
                    "spo2": 90,
                    "capturedAt": "2025-08-01T12:15:00Z"
                },
                {
                    "readingId": "55555555-5555-5555-5555-555555555555",
                    "patientId": "p-001",
                    "type": "BP",
                    "systolic": 128,
                    "diastolic": 82,
                    "capturedAt": "2025-08-01T12:20:00Z"
                }
            ]
            """;

        // When & Then
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(batchReadings)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(4)
            .jsonPath("$[0].readingId").isEqualTo("11111111-1111-1111-1111-111111111111")
            .jsonPath("$[0].readingType").isEqualTo("BP")
            .jsonPath("$[0].alertType").isEqualTo("CRITICAL")
            .jsonPath("$[1].readingId").isEqualTo("22222222-2222-2222-2222-222222222222")
            .jsonPath("$[1].readingType").isEqualTo("HR")
            .jsonPath("$[1].alertType").isEqualTo("HIGH")
            .jsonPath("$[2].readingId").isEqualTo("33333333-3333-3333-3333-333333333333")
            .jsonPath("$[2].readingType").isEqualTo("HR")
            .jsonPath("$[2].alertType").isEqualTo("LOW")
            .jsonPath("$[3].readingId").isEqualTo("44444444-4444-4444-4444-444444444444")
            .jsonPath("$[3].readingType").isEqualTo("SPO2")
            .jsonPath("$[3].alertType").isEqualTo("LOW");

        verify(vitalService, times(1)).processReadings(anyList());
    }
    
    // Helper method to create test alerts
    private Alert createAlert(String readingId, String readingType, String alertType, String thresholdViolated, String readingValue) {
        Alert alert = new Alert();
        alert.setAlertId("alert-" + readingId);
        alert.setPatientId("p-001");
        alert.setReadingId(readingId);
        alert.setReadingType(readingType);
        alert.setAlertType(alertType);
        alert.setThresholdViolated(thresholdViolated);
        alert.setReadingValue(readingValue);
        alert.setTriggeredAt(LocalDateTime.now());
        return alert;
    }
}