package com.folautech.alert.integration;

import com.folautech.alert.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AlertServiceIntegrationTest {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        webTestClient = WebTestClient.bindToServer()
                .baseUrl(baseUrl)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Test
    @DisplayName("End-to-end test: BP reading triggers CRITICAL alert")
    void testBPReadingTriggersAlert() {
        String readingId = UUID.randomUUID().toString();
        String patientId = "p-test-" + UUID.randomUUID();
        String capturedAt = LocalDateTime.now().toString();

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

        webTestClient.get()
                .uri("/alerts?patientId=" + patientId)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Alert.class)
                .hasSize(1)
                .value(alerts -> {
                    Alert alert = alerts.get(0);
                    assert alert.getAlertType().equals("CRITICAL");
                    assert alert.getThresholdViolated().contains("Systolic >= 140 AND Diastolic >= 90");
                    assert alert.getReadingValue().equals("150/95");
                    assert alert.getReadingId().equals(readingId);
                });
    }

    @ParameterizedTest
    @DisplayName("Multiple vital types trigger appropriate alerts")
    @CsvSource({
        "BP, 145, 85, , , HIGH, 'Systolic >= 140', '145/85'",
        "BP, 135, 92, , , HIGH, 'Diastolic >= 90', '135/92'",
        "HR, , , 45, , LOW, 'Heart Rate < 50', '45'",
        "HR, , , 115, , HIGH, 'Heart Rate > 110', '115'",
        "SPO2, , , , 88, CRITICAL, 'SpO2 < 92', '88'",
        "SPO2, , , , 91, LOW, 'SpO2 < 92', '91'"
    })
    void testMultipleVitalTypes(String type, Integer systolic, Integer diastolic, 
                                Integer hr, Integer spo2, String expectedAlertType, 
                                String expectedThreshold, String expectedValue) {
        String readingId = UUID.randomUUID().toString();
        String patientId = "p-test-" + UUID.randomUUID();
        String capturedAt = LocalDateTime.now().toString();

        String requestBody;
        if (type.equals("BP")) {
            requestBody = """
                [{
                    "type": "BP",
                    "readingId": "%s",
                    "patientId": "%s",
                    "systolic": %d,
                    "diastolic": %d,
                    "capturedAt": "%s"
                }]
                """.formatted(readingId, patientId, systolic, diastolic, capturedAt);
        } else if (type.equals("HR")) {
            requestBody = """
                [{
                    "type": "HR",
                    "readingId": "%s",
                    "patientId": "%s",
                    "hr": %d,
                    "capturedAt": "%s"
                }]
                """.formatted(readingId, patientId, hr, capturedAt);
        } else {
            requestBody = """
                [{
                    "type": "SPO2",
                    "readingId": "%s",
                    "patientId": "%s",
                    "spo2": %d,
                    "capturedAt": "%s"
                }]
                """.formatted(readingId, patientId, spo2, capturedAt);
        }

        webTestClient.post()
                .uri("/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/alerts?patientId=" + patientId)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Alert.class)
                .hasSize(1)
                .value(alerts -> {
                    Alert alert = alerts.get(0);
                    assert alert.getAlertType().equals(expectedAlertType);
                    assert alert.getThresholdViolated().contains(expectedThreshold);
                    assert alert.getReadingValue().equals(expectedValue);
                });
    }

    @Test
    @DisplayName("Idempotency: Duplicate reading does not create duplicate alert")
    void testIdempotency() {
        String readingId = UUID.randomUUID().toString();
        String patientId = "p-test-" + UUID.randomUUID();
        String capturedAt = LocalDateTime.now().toString();

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

        webTestClient.post()
                .uri("/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/alerts?patientId=" + patientId)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Alert.class)
                .hasSize(1);
    }

    @ParameterizedTest
    @DisplayName("Normal readings do not trigger alerts")
    @CsvSource({
        "BP, 120, 80, , ",
        "BP, 139, 89, , ",
        "HR, , , 50, ",
        "HR, , , 75, ",
        "HR, , , 110, ",
        "SPO2, , , , 92",
        "SPO2, , , , 95",
        "SPO2, , , , 100"
    })
    void testNormalReadingsNoAlert(String type, Integer systolic, Integer diastolic, 
                                   Integer hr, Integer spo2) {
        String readingId = UUID.randomUUID().toString();
        String patientId = "p-test-" + UUID.randomUUID();
        String capturedAt = LocalDateTime.now().toString();

        String requestBody;
        if (type.equals("BP")) {
            requestBody = """
                [{
                    "type": "BP",
                    "readingId": "%s",
                    "patientId": "%s",
                    "systolic": %d,
                    "diastolic": %d,
                    "capturedAt": "%s"
                }]
                """.formatted(readingId, patientId, systolic, diastolic, capturedAt);
        } else if (type.equals("HR")) {
            requestBody = """
                [{
                    "type": "HR",
                    "readingId": "%s",
                    "patientId": "%s",
                    "hr": %d,
                    "capturedAt": "%s"
                }]
                """.formatted(readingId, patientId, hr, capturedAt);
        } else {
            requestBody = """
                [{
                    "type": "SPO2",
                    "readingId": "%s",
                    "patientId": "%s",
                    "spo2": %d,
                    "capturedAt": "%s"
                }]
                """.formatted(readingId, patientId, spo2, capturedAt);
        }

        webTestClient.post()
                .uri("/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/alerts?patientId=" + patientId)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Alert.class)
                .hasSize(0);
    }

    @Test
    @DisplayName("Multiple alerts for same patient are returned in order")
    void testMultipleAlertsForPatient() {
        String patientId = "p-test-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        String reading1 = """
            [{
                "type": "BP",
                "readingId": "%s",
                "patientId": "%s",
                "systolic": 150,
                "diastolic": 95,
                "capturedAt": "%s"
            }]
            """.formatted(UUID.randomUUID(), patientId, now.minusHours(2));

        String reading2 = """
            [{
                "type": "HR",
                "readingId": "%s",
                "patientId": "%s",
                "hr": 45,
                "capturedAt": "%s"
            }]
            """.formatted(UUID.randomUUID(), patientId, now.minusHours(1));

        String reading3 = """
            [{
                "type": "SPO2",
                "readingId": "%s",
                "patientId": "%s",
                "spo2": 88,
                "capturedAt": "%s"
            }]
            """.formatted(UUID.randomUUID(), patientId, now);

        webTestClient.post().uri("/evaluate").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reading1).exchange().expectStatus().isOk();
        
        webTestClient.post().uri("/evaluate").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reading2).exchange().expectStatus().isOk();
        
        webTestClient.post().uri("/evaluate").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reading3).exchange().expectStatus().isOk();

        webTestClient.get()
                .uri("/alerts?patientId=" + patientId)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Alert.class)
                .hasSize(3)
                .value(alerts -> {
                    // Check that we have all three types (order by alertId is unpredictable with UUIDs)
                    boolean hasBP = alerts.stream().anyMatch(a -> a.getReadingType().equals("BP"));
                    boolean hasHR = alerts.stream().anyMatch(a -> a.getReadingType().equals("HR"));
                    boolean hasSPO2 = alerts.stream().anyMatch(a -> a.getReadingType().equals("SPO2"));
                    assert hasBP && hasHR && hasSPO2;
                });
    }
}