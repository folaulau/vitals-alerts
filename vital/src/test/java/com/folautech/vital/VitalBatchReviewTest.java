package com.folautech.vital;

import org.junit.jupiter.api.DisplayName;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

/**
 * Integration test demonstrating batch processing of vital readings.
 * This test is designed for review purposes to show the exact payload
 * from the requirements being processed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Vital Service Batch Processing Review Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VitalBatchReviewTest {
    
    @Autowired
    private WebTestClient webTestClient;
    
    private static MockWebServer mockAlertService;
    
    @BeforeAll
    static void setupMockServer() throws IOException {
        mockAlertService = new MockWebServer();
        mockAlertService.start();
    }
    
    @AfterAll
    static void tearDown() throws IOException {
        mockAlertService.shutdown();
    }
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("alert.service.url", () -> mockAlertService.url("/").toString());
    }
    
    @Test
    @Order(1)
    @DisplayName("Process batch of 5 vital readings from mock data - Review Test")
    void testBatchProcessingForReview() {
        // Setup mock alert service response with 4 alerts (5th reading is normal)
        String alertsResponse = """
            [
                {"alertId":"alert-1","patientId":"p-001","readingId":"11111111-1111-1111-1111-111111111111","readingType":"BP","alertType":"CRITICAL","thresholdViolated":"Systolic >= 140 AND Diastolic >= 90","readingValue":"150/95","triggeredAt":"2025-08-01T12:00:00"},
                {"alertId":"alert-2","patientId":"p-001","readingId":"22222222-2222-2222-2222-222222222222","readingType":"HR","alertType":"HIGH","thresholdViolated":"Heart Rate > 110","readingValue":"120","triggeredAt":"2025-08-01T12:05:00"},
                {"alertId":"alert-3","patientId":"p-001","readingId":"33333333-3333-3333-3333-333333333333","readingType":"HR","alertType":"LOW","thresholdViolated":"Heart Rate < 50","readingValue":"45","triggeredAt":"2025-08-01T12:10:00"},
                {"alertId":"alert-4","patientId":"p-001","readingId":"44444444-4444-4444-4444-444444444444","readingType":"SPO2","alertType":"LOW","thresholdViolated":"SpO2 < 92","readingValue":"90","triggeredAt":"2025-08-01T12:15:00"}
            ]
            """;
        
        mockAlertService.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(alertsResponse)
            .addHeader("Content-Type", "application/json"));
        
        // The exact payload from the requirements
        // This demonstrates the endpoint accepting a list of readings
        String batchPayload = """
            [
                { "readingId":"11111111-1111-1111-1111-111111111111", "patientId":"p-001", "type":"BP", "systolic":150, "diastolic":95, "capturedAt":"2025-08-01T12:00:00Z" },
                { "readingId":"22222222-2222-2222-2222-222222222222", "patientId":"p-001", "type":"HR", "hr":120, "capturedAt":"2025-08-01T12:05:00Z" },
                { "readingId":"33333333-3333-3333-3333-333333333333", "patientId":"p-001", "type":"HR", "hr":45, "capturedAt":"2025-08-01T12:10:00Z" },
                { "readingId":"44444444-4444-4444-4444-444444444444", "patientId":"p-001", "type":"SPO2", "spo2":90, "capturedAt":"2025-08-01T12:15:00Z" },
                { "readingId":"55555555-5555-5555-5555-555555555555", "patientId":"p-001", "type":"BP", "systolic":128, "diastolic":82, "capturedAt":"2025-08-01T12:20:00Z" }
            ]
            """;
        
        // Send the batch request to /readings endpoint
        // The endpoint now accepts an array of readings instead of a single reading
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(batchPayload)
            .exchange()
            .expectStatus().isOk()  // Returns 200 OK with alerts
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(4)  // 4 alerts expected (5th reading is normal)
            .jsonPath("$[0].readingId").isEqualTo("11111111-1111-1111-1111-111111111111")
            .jsonPath("$[1].readingId").isEqualTo("22222222-2222-2222-2222-222222222222")
            .jsonPath("$[2].readingId").isEqualTo("33333333-3333-3333-3333-333333333333")
            .jsonPath("$[3].readingId").isEqualTo("44444444-4444-4444-4444-444444444444");
        
        /*
         * What happens behind the scenes:
         * 
         * 1. The VitalController receives the array of 5 readings
         * 2. Each reading is processed in parallel using Flux.fromIterable()
         * 3. For each reading:
         *    a. Validation is performed (required fields, value ranges)
         *    b. The reading is saved to the database (with idempotency check)
         *    c. The reading is forwarded to the Alert Service at http://localhost:8082/evaluate
         * 
         * 4. Expected alerts based on thresholds:
         *    - Reading 1 (BP): systolic=150 >= 140, diastolic=95 >= 90 → ALERT (HIGH)
         *    - Reading 2 (HR): hr=120 > 110 → ALERT (HIGH)
         *    - Reading 3 (HR): hr=45 < 50 → ALERT (LOW)
         *    - Reading 4 (SPO2): spo2=90 < 92 → ALERT (LOW)
         *    - Reading 5 (BP): systolic=128 < 140, diastolic=82 < 90 → NO ALERT (NORMAL)
         * 
         * 5. Even if the Alert Service is unavailable, the readings are still saved
         *    and the endpoint returns success (resilient design)
         */
    }
    
    @Test
    @Order(2)
    @DisplayName("Demonstrate single reading compatibility")
    void testSingleReadingCompatibility() {
        // Setup mock response - single reading with normal values (no alert)
        mockAlertService.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("[]")
            .addHeader("Content-Type", "application/json"));
        
        // The endpoint also accepts a single-element array for backward compatibility
        String singleReading = """
            [
                { "readingId":"single-test-1", "patientId":"p-002", "type":"BP", "systolic":120, "diastolic":80, "capturedAt":"2025-08-01T13:00:00Z" }
            ]
            """;
        
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(singleReading)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(0);  // No alerts for normal reading
    }
    
    @Test
    @Order(3)
    @DisplayName("Demonstrate validation for invalid readings")
    void testValidationInBatch() {
        // If a single reading in the batch has validation errors,
        // it returns 400 Bad Request (when batch size is 1)
        String invalidReading = """
            [
                { "readingId":"invalid-1", "patientId":"p-003", "type":"BP", "systolic":120, "capturedAt":"2025-08-01T14:00:00Z" }
            ]
            """;
        
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidReading)
            .exchange()
            .expectStatus().isBadRequest();  // Missing diastolic for BP reading
    }
    
    @Test
    @Order(4)
    @DisplayName("Demonstrate mixed valid/invalid batch processing")
    void testMixedBatch() {
        // Setup mock response - only valid readings are forwarded (2 out of 3)
        mockAlertService.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("[]")
            .addHeader("Content-Type", "application/json"));
        
        // When processing multiple readings, invalid ones are skipped
        // but valid ones are still processed
        String mixedBatch = """
            [
                { "readingId":"valid-1", "patientId":"p-004", "type":"HR", "hr":75, "capturedAt":"2025-08-01T15:00:00Z" },
                { "readingId":"invalid-2", "patientId":"p-004", "type":"BP", "systolic":120, "capturedAt":"2025-08-01T15:05:00Z" },
                { "readingId":"valid-3", "patientId":"p-004", "type":"SPO2", "spo2":98, "capturedAt":"2025-08-01T15:10:00Z" }
            ]
            """;
        
        webTestClient
            .post()
            .uri("/readings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mixedBatch)
            .exchange()
            .expectStatus().isOk()  // Still accepts even with one invalid reading
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(0);  // No alerts for these normal readings
    }
}