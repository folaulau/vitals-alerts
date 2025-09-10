package com.folautech.vital;

import com.folautech.vital.model.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VitalTransactionalTest {
    
    @Autowired
    private WebTestClient webTestClient;
    
    private static MockWebServer mockAlertService;
    
    @BeforeAll
    static void setupMockServer() throws IOException {
        mockAlertService = new MockWebServer();
        mockAlertService.start();
    }
    
    @AfterAll
    static void tearDownMockServer() throws IOException {
        mockAlertService.shutdown();
    }
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("alert.service.url", () -> mockAlertService.url("/").toString());
        registry.add("alert.service.port", () -> String.valueOf(mockAlertService.getPort()));
    }
    
    @BeforeEach
    void setupMockResponse() {
        // Clear any previous responses
        while (mockAlertService.getRequestCount() > 0) {
            try {
                mockAlertService.takeRequest();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    @Test
    @DisplayName("Should rollback all readings when one fails in transactional mode")
    void testTransactionalRollback() {
        String now = LocalDateTime.now().toString();
        String patientId = "p-" + UUID.randomUUID();
        
        // Create a batch with one invalid reading in the middle
        List<VitalReading> readings = List.of(
            new BPReading(UUID.randomUUID().toString(), patientId, now, 120, 80),
            new HRReading(UUID.randomUUID().toString(), patientId, now, -10), // Invalid HR
            new SPO2Reading(UUID.randomUUID().toString(), patientId, now, 95)
        );
        
        // Submit with transactional=true
        webTestClient.post()
            .uri("/readings?transactional=true")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(readings)
            .exchange()
            .expectStatus().isBadRequest();
        
        // Verify that NO readings were saved by trying to retrieve them
        webTestClient.get()
            .uri("/readings")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[?(@.patientId == '" + patientId + "')]").doesNotExist();
    }
    
    @Test
    @DisplayName("Should save all readings when all are valid in transactional mode")
    void testTransactionalSuccess() {
        String now = LocalDateTime.now().toString();
        String patientId = "p-" + UUID.randomUUID();
        
        // Create a batch with all valid readings
        List<VitalReading> readings = List.of(
            new BPReading(UUID.randomUUID().toString(), patientId, now, 120, 80),
            new HRReading(UUID.randomUUID().toString(), patientId, now, 75),
            new SPO2Reading(UUID.randomUUID().toString(), patientId, now, 95)
        );
        
        // Mock alert service response
        String alertResponse = """
            [
                {
                    "alertId": "alert-1",
                    "patientId": "%s",
                    "readingId": "reading-1",
                    "readingType": "BP",
                    "alertType": "NONE",
                    "status": "RESOLVED",
                    "triggeredAt": "%s"
                }
            ]
            """.formatted(patientId, now);
        
        mockAlertService.enqueue(new MockResponse()
            .setBody(alertResponse)
            .setHeader("Content-Type", "application/json"));
        
        // Submit with transactional=true
        webTestClient.post()
            .uri("/readings?transactional=true")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(readings)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray();
        
        // Verify that all readings were saved
        webTestClient.get()
            .uri("/readings")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[?(@.patientId == '" + patientId + "')]").isArray()
            .jsonPath("$[?(@.patientId == '" + patientId + "')].length()").isEqualTo(3);
    }
    
    @Test
    @DisplayName("Should allow partial success when transactional=false")
    void testNonTransactionalPartialSuccess() {
        String now = LocalDateTime.now().toString();
        String patientId = "p-" + UUID.randomUUID();
        
        // Create a batch with one invalid reading in the middle
        List<VitalReading> readings = List.of(
            new BPReading(UUID.randomUUID().toString(), patientId, now, 120, 80),
            new HRReading(UUID.randomUUID().toString(), patientId, now, -10), // Invalid HR
            new SPO2Reading(UUID.randomUUID().toString(), patientId, now, 95)
        );
        
        // Mock alert service response for successful readings
        String alertResponse = """
            [
                {
                    "alertId": "alert-1",
                    "patientId": "%s",
                    "readingId": "reading-1",
                    "readingType": "BP",
                    "alertType": "NONE",
                    "status": "RESOLVED",
                    "triggeredAt": "%s"
                }
            ]
            """.formatted(patientId, now);
        
        mockAlertService.enqueue(new MockResponse()
            .setBody(alertResponse)
            .setHeader("Content-Type", "application/json"));
        
        // Submit with transactional=false (default)
        webTestClient.post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(readings)
            .exchange()
            .expectStatus().isOk();
        
        // Verify that 2 readings were saved (invalid one skipped)
        webTestClient.get()
            .uri("/readings")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[?(@.patientId == '" + patientId + "')]").isArray()
            .jsonPath("$[?(@.patientId == '" + patientId + "')].length()").isEqualTo(2);
    }
}