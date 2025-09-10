package com.folautech.vital;

import com.folautech.vital.model.*;
import com.folautech.vital.repository.VitalRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, 
    properties = {"alert.service.url=http://localhost:9999", "alert.service.timeout.seconds=2"})
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VitalServiceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;
    
    @Autowired
    private VitalRepository vitalRepository;
    
    private static MockWebServer mockAlertService;
    
    @BeforeAll
    static void setUp() throws IOException {
        mockAlertService = new MockWebServer();
        mockAlertService.start(9999);
    }
    
    @AfterAll
    static void tearDown() throws IOException {
        mockAlertService.shutdown();
    }
    
    
    @AfterEach
    void cleanupDatabase() throws InterruptedException {
        vitalRepository.deleteAll().block();
        // Clear any pending requests from MockWebServer to avoid cross-test pollution
        while (mockAlertService.takeRequest(100, TimeUnit.MILLISECONDS) != null) {
            // Drain remaining requests
        }
    }
    
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public WebClient.Builder testWebClientBuilder() {
            return WebClient.builder()
                .baseUrl("http://localhost:9999");
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Should successfully process BP reading")
    void testProcessBPReading() throws InterruptedException {
        // Setup mock alert service response with alert
        String alertResponse = """
            [
            {
                "alertId": "alert-1",
                "patientId": "p-001",
                "readingId": "11111111-1111-1111-1111-111111111111",
                "readingType": "BP",
                "alertType": "CRITICAL",
                "thresholdViolated": "Systolic >= 140 AND Diastolic >= 90",
                "readingValue": "150/95",
                "triggeredAt": "2025-08-01T12:00:00"
            }
        ]""";
        mockAlertService.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(alertResponse)
            .addHeader("Content-Type", "application/json"));
        
        String requestBody = """
            [{
                "readingId": "11111111-1111-1111-1111-111111111111",
                "patientId": "p-001",
                "type": "BP",
                "systolic": 150,
                "diastolic": 95,
                "capturedAt": "2025-08-01T12:00:00Z"
            }]
            """;
        
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].readingId").isEqualTo("11111111-1111-1111-1111-111111111111")
            .jsonPath("$[0].alertType").isEqualTo("CRITICAL");
        
        // Verify the reading was saved
        StepVerifier.create(vitalRepository.existsByReadingId("11111111-1111-1111-1111-111111111111"))
            .expectNext(true)
            .verifyComplete();
        
        // Verify alert service was called
        RecordedRequest recordedRequest = mockAlertService.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getPath()).isEqualTo("/evaluate");
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    }
    
    @Test
    @Order(2)
    @DisplayName("Should successfully process HR reading")
    void testProcessHRReading() throws InterruptedException {
        String alertResponse = """
            [
            {
                "alertId": "alert-2",
                "patientId": "p-001",
                "readingId": "22222222-2222-2222-2222-222222222222",
                "readingType": "HR",
                "alertType": "HIGH",
                "thresholdViolated": "Heart Rate > 110",
                "readingValue": "120",
                "triggeredAt": "2025-08-01T12:05:00"
            }
        ]""";
        mockAlertService.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(alertResponse)
            .addHeader("Content-Type", "application/json"));
        
        String requestBody = """
            [{
                "readingId": "22222222-2222-2222-2222-222222222222",
                "patientId": "p-001",
                "type": "HR",
                "hr": 120,
                "capturedAt": "2025-08-01T12:05:00Z"
            }]
            """;
        
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].readingId").isEqualTo("22222222-2222-2222-2222-222222222222")
            .jsonPath("$[0].alertType").isEqualTo("HIGH");
        
        // Verify the reading was saved
        StepVerifier.create(vitalRepository.existsByReadingId("22222222-2222-2222-2222-222222222222"))
            .expectNext(true)
            .verifyComplete();
        
        // Verify alert service was called
        RecordedRequest recordedRequest = mockAlertService.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getPath()).isEqualTo("/evaluate");
    }
    
    @Test
    @Order(3)
    @DisplayName("Should successfully process SPO2 reading")
    void testProcessSPO2Reading() throws InterruptedException {
        String alertResponse = """
            [
            {
                "alertId": "alert-3",
                "patientId": "p-001",
                "readingId": "44444444-4444-4444-4444-444444444444",
                "readingType": "SPO2",
                "alertType": "LOW",
                "thresholdViolated": "SpO2 < 92",
                "readingValue": "90",
                "triggeredAt": "2025-08-01T12:15:00"
            }
        ]""";
        mockAlertService.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(alertResponse)
            .addHeader("Content-Type", "application/json"));
        
        String requestBody = """
            [{
                "readingId": "44444444-4444-4444-4444-444444444444",
                "patientId": "p-001",
                "type": "SPO2",
                "spo2": 90,
                "capturedAt": "2025-08-01T12:15:00Z"
            }]
            """;
        
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].readingId").isEqualTo("44444444-4444-4444-4444-444444444444")
            .jsonPath("$[0].alertType").isEqualTo("LOW");
        
        // Verify the reading was saved
        StepVerifier.create(vitalRepository.existsByReadingId("44444444-4444-4444-4444-444444444444"))
            .expectNext(true)
            .verifyComplete();
        
        RecordedRequest recordedRequest = mockAlertService.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
    }
    
    @Test
    @Order(4)
    @DisplayName("Should handle idempotency - duplicate reading ID")
    void testIdempotency() throws InterruptedException {
        // Normal range BP, no alert
        mockAlertService.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("[]")
            .addHeader("Content-Type", "application/json"));
        // Second request won't reach alert service due to idempotency
        
        String requestBody = """
            [{
                "readingId": "55555555-5555-5555-5555-555555555555",
                "patientId": "p-001",
                "type": "BP",
                "systolic": 128,
                "diastolic": 82,
                "capturedAt": "2025-08-01T12:20:00Z"
            }]
            """;
        
        // First request
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
        
        // Verify alert service was called once
        RecordedRequest firstRequest = mockAlertService.takeRequest(2, TimeUnit.SECONDS);
        assertThat(firstRequest).isNotNull();
        
        // Second request with same readingId (should be idempotent)
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(0); // Returns empty alerts for duplicate
        
        // Verify alert service was NOT called again
        RecordedRequest secondRequest = mockAlertService.takeRequest(100, TimeUnit.MILLISECONDS);
        assertThat(secondRequest).isNull();
        
        // Verify only one record exists
        StepVerifier.create(vitalRepository.count())
            .expectNext(1L)
            .verifyComplete();
    }
    
    @Test
    @Order(5)
    @DisplayName("Should validate BP reading - missing systolic")
    void testValidationBPMissingSystolic() {
        String requestBody = """
            [{
                "readingId": "invalid-bp-1",
                "patientId": "p-001",
                "type": "BP",
                "diastolic": 80,
                "capturedAt": "2025-08-01T12:00:00Z"
            }]
            """;
        
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isBadRequest();
    }
    
    @Test
    @Order(6)
    @DisplayName("Should validate HR reading - missing hr value")
    void testValidationHRMissingValue() {
        String requestBody = """
            [{
                "readingId": "invalid-hr-1",
                "patientId": "p-001",
                "type": "HR",
                "capturedAt": "2025-08-01T12:00:00Z"
            }]
            """;
        
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isBadRequest();
    }
    
    @Test
    @Order(7)
    @DisplayName("Should validate SPO2 reading - out of range")
    void testValidationSPO2OutOfRange() {
        String requestBody = """
            [{
                "readingId": "invalid-spo2-1",
                "patientId": "p-001",
                "type": "SPO2",
                "spo2": 150,
                "capturedAt": "2025-08-01T12:00:00Z"
            }]
            """;
        
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isBadRequest();
    }
    
    @Test
    @Order(8)
    @DisplayName("Should validate required fields - missing readingId")
    void testValidationMissingReadingId() {
        String requestBody = """
            [{
                "patientId": "p-001",
                "type": "BP",
                "systolic": 120,
                "diastolic": 80,
                "capturedAt": "2025-08-01T12:00:00Z"
            }]
            """;
        
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isBadRequest();
    }
    
    @Test
    @Order(9)
    @DisplayName("Should continue processing even if alert service is down")
    void testAlertServiceDown() {
        // Don't enqueue any response - simulating service down
        
        String requestBody = """
            [{
                "readingId": "service-down-test",
                "patientId": "p-001",
                "type": "BP",
                "systolic": 120,
                "diastolic": 80,
                "capturedAt": "2025-08-01T12:00:00Z"
            }]
            """;
        
        webTestClient
            .mutate()
            .responseTimeout(Duration.ofSeconds(10))
            .build()
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(0); // Alert service down, returns empty alerts
        
        // Verify the reading was still saved despite alert service being down
        StepVerifier.create(vitalRepository.existsByReadingId("service-down-test"))
            .expectNext(true)
            .verifyComplete();
    }
}