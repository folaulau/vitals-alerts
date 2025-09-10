package com.folautech.vital.service;

import com.folautech.vital.model.*;
import com.folautech.vital.repository.VitalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VitalServiceTest {

    @Mock
    private VitalRepository vitalRepository;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private VitalService vitalService;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.build()).thenReturn(webClient);
        vitalService = new VitalService(vitalRepository, webClientBuilder);
        ReflectionTestUtils.setField(vitalService, "alertServiceUrl", "http://localhost:8082");
        
        // Setup default mock behavior for WebClient chain
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(org.springframework.http.ResponseEntity.accepted().build()));
        
        // Setup lenient mock for repository save (in case it gets called)
        lenient().when(vitalRepository.save(any(VitalReadingEntity.class)))
            .thenReturn(Mono.just(VitalReadingEntity.forBP(
                "default", "default", 120, 80,
                java.time.LocalDateTime.now()
            )));
    }

    @Test
    @DisplayName("Should process valid BP reading successfully")
    void testProcessValidBPReading() {
        // Given
        BPReading reading = new BPReading(
            "test-bp-1",
            "p-001",
            "2025-08-01T12:00:00Z",
            120,
            80
        );

        VitalReadingEntity entity = VitalReadingEntity.forBP(
            "test-bp-1",
            "p-001",
            120,
            80,
            java.time.LocalDateTime.parse("2025-08-01T12:00:00")
        );

        when(vitalRepository.existsByReadingId("test-bp-1"))
            .thenReturn(Mono.just(false));
        when(vitalRepository.save(any(VitalReadingEntity.class)))
            .thenReturn(Mono.just(entity));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .verifyComplete();

        verify(vitalRepository).existsByReadingId("test-bp-1");
        verify(vitalRepository).save(any(VitalReadingEntity.class));
    }

    @Test
    @DisplayName("Should process valid HR reading successfully")
    void testProcessValidHRReading() {
        // Given
        HRReading reading = new HRReading(
            "test-hr-1",
            "p-001",
            "2025-08-01T12:00:00Z",
            75
        );

        VitalReadingEntity entity = VitalReadingEntity.forHR(
            "test-hr-1",
            "p-001",
            75,
            java.time.LocalDateTime.parse("2025-08-01T12:00:00")
        );

        when(vitalRepository.existsByReadingId("test-hr-1"))
            .thenReturn(Mono.just(false));
        when(vitalRepository.save(any(VitalReadingEntity.class)))
            .thenReturn(Mono.just(entity));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .verifyComplete();

        verify(vitalRepository).existsByReadingId("test-hr-1");
        verify(vitalRepository).save(any(VitalReadingEntity.class));
    }

    @Test
    @DisplayName("Should process valid SPO2 reading successfully")
    void testProcessValidSPO2Reading() {
        // Given
        SPO2Reading reading = new SPO2Reading(
            "test-spo2-1",
            "p-001",
            "2025-08-01T12:00:00Z",
            98
        );

        VitalReadingEntity entity = VitalReadingEntity.forSPO2(
            "test-spo2-1",
            "p-001",
            98,
            java.time.LocalDateTime.parse("2025-08-01T12:00:00")
        );

        when(vitalRepository.existsByReadingId("test-spo2-1"))
            .thenReturn(Mono.just(false));
        when(vitalRepository.save(any(VitalReadingEntity.class)))
            .thenReturn(Mono.just(entity));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .verifyComplete();

        verify(vitalRepository).existsByReadingId("test-spo2-1");
        verify(vitalRepository).save(any(VitalReadingEntity.class));
    }

    @Test
    @DisplayName("Should handle duplicate reading (idempotency)")
    void testIdempotency() {
        // Given
        BPReading reading = new BPReading(
            "duplicate-1",
            "p-001",
            "2025-08-01T12:00:00Z",
            120,
            80
        );

        when(vitalRepository.existsByReadingId("duplicate-1"))
            .thenReturn(Mono.just(true));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .expectError(RuntimeException.class)
            .verify();

        // Verify only the idempotency check was made
        verify(vitalRepository).existsByReadingId("duplicate-1");
    }

    @Test
    @DisplayName("Should validate BP reading - missing systolic")
    void testValidateBPMissingSystolic() {
        // Given
        BPReading reading = new BPReading(
            "invalid-bp-1",
            "p-001",
            "2025-08-01T12:00:00Z",
            null,
            80
        );

        // Mock repository (even though validation should fail first)
        when(vitalRepository.existsByReadingId(anyString()))
            .thenReturn(Mono.just(false));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .expectErrorMatches(error -> error.getMessage().contains("systolic and diastolic are required for BP readings"))
            .verify();
    }

    @Test
    @DisplayName("Should validate BP reading - missing diastolic")
    void testValidateBPMissingDiastolic() {
        // Given
        BPReading reading = new BPReading(
            "invalid-bp-2",
            "p-001",
            "2025-08-01T12:00:00Z",
            120,
            null
        );

        // Mock repository
        when(vitalRepository.existsByReadingId(anyString()))
            .thenReturn(Mono.just(false));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .expectErrorMatches(error -> error.getMessage().contains("systolic and diastolic are required for BP readings"))
            .verify();
    }

    @Test
    @DisplayName("Should validate HR reading - missing hr value")
    void testValidateHRMissingValue() {
        // Given
        HRReading reading = new HRReading(
            "invalid-hr-1",
            "p-001",
            "2025-08-01T12:00:00Z",
            null
        );

        // Mock repository
        when(vitalRepository.existsByReadingId(anyString()))
            .thenReturn(Mono.just(false));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .expectErrorMatches(error -> error.getMessage().contains("hr is required for HR readings"))
            .verify();
    }

    @Test
    @DisplayName("Should validate SPO2 reading - out of range")
    void testValidateSPO2OutOfRange() {
        // Given
        SPO2Reading reading = new SPO2Reading(
            "invalid-spo2-1",
            "p-001",
            "2025-08-01T12:00:00Z",
            150
        );

        // Mock repository
        when(vitalRepository.existsByReadingId(anyString()))
            .thenReturn(Mono.just(false));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .expectErrorMatches(error -> error.getMessage().contains("spo2 must be between 0 and 100"))
            .verify();
    }

    @Test
    @DisplayName("Should validate missing readingId")
    void testValidateMissingReadingId() {
        // Given
        BPReading reading = new BPReading(
            null,
            "p-001",
            "2025-08-01T12:00:00Z",
            120,
            80
        );

        // Mock repository
        when(vitalRepository.existsByReadingId(any()))
            .thenReturn(Mono.just(false));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .expectErrorMatches(error -> error.getMessage().contains("readingId is required"))
            .verify();
    }

    @Test
    @DisplayName("Should validate missing patientId")
    void testValidateMissingPatientId() {
        // Given
        BPReading reading = new BPReading(
            "test-1",
            null,
            "2025-08-01T12:00:00Z",
            120,
            80
        );

        // Mock repository
        when(vitalRepository.existsByReadingId(anyString()))
            .thenReturn(Mono.just(false));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .expectErrorMatches(error -> error.getMessage().contains("patientId is required"))
            .verify();
    }

    @Test
    @DisplayName("Should continue processing even if alert service fails")
    void testAlertServiceFailure() {
        // Given
        BPReading reading = new BPReading(
            "test-bp-1",
            "p-001",
            "2025-08-01T12:00:00Z",
            120,
            80
        );

        VitalReadingEntity entity = VitalReadingEntity.forBP(
            "test-bp-1",
            "p-001",
            120,
            80,
            java.time.LocalDateTime.parse("2025-08-01T12:00:00")
        );

        when(vitalRepository.existsByReadingId("test-bp-1"))
            .thenReturn(Mono.just(false));
        when(vitalRepository.save(any(VitalReadingEntity.class)))
            .thenReturn(Mono.just(entity));
        
        // Override the default WebClient mock to fail
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.error(new RuntimeException("Connection refused")));

        // When & Then - Should complete successfully despite alert service failure
        StepVerifier.create(vitalService.processReading(reading))
            .verifyComplete();

        verify(vitalRepository).existsByReadingId("test-bp-1");
        verify(vitalRepository).save(any(VitalReadingEntity.class));
    }
}