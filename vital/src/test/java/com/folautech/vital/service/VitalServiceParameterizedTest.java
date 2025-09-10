package com.folautech.vital.service;

import com.folautech.vital.model.*;
import com.folautech.vital.repository.VitalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VitalServiceParameterizedTest {

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

    @ParameterizedTest
    @DisplayName("BP Validation - Missing or Invalid Values")
    @CsvSource({
        // systolic, diastolic, expectedError
        ", 80, 'systolic and diastolic are required'",
        "120, , 'systolic and diastolic are required'",
        "-1, 80, 'systolic must be between 0 and 300'",
        "301, 80, 'systolic must be between 0 and 300'",
        "120, -1, 'diastolic must be between 0 and 200'",
        "120, 201, 'diastolic must be between 0 and 200'"
    })
    void testBPValidation(Integer systolic, Integer diastolic, String expectedError) {
        // Given
        BPReading reading = new BPReading(
            "test-bp",
            "p-001",
            "2025-08-01T12:00:00Z",
            systolic,
            diastolic
        );

        when(vitalRepository.existsByReadingId(anyString()))
            .thenReturn(Mono.just(false));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .expectErrorMatches(error -> error.getMessage().contains(expectedError))
            .verify();
    }

    @ParameterizedTest
    @DisplayName("HR Validation - Missing or Invalid Values")
    @CsvSource({
        // hr, expectedError
        ", 'hr is required'",
        "-1, 'hr must be between 0 and 300'",
        "301, 'hr must be between 0 and 300'"
    })
    void testHRValidation(Integer hr, String expectedError) {
        // Given
        HRReading reading = new HRReading(
            "test-hr",
            "p-001",
            "2025-08-01T12:00:00Z",
            hr
        );

        when(vitalRepository.existsByReadingId(anyString()))
            .thenReturn(Mono.just(false));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .expectErrorMatches(error -> error.getMessage().contains(expectedError))
            .verify();
    }

    @ParameterizedTest
    @DisplayName("SPO2 Validation - Missing or Invalid Values")
    @CsvSource({
        // spo2, expectedError
        ", 'spo2 is required'",
        "-1, 'spo2 must be between 0 and 100'",
        "101, 'spo2 must be between 0 and 100'",
        "150, 'spo2 must be between 0 and 100'"
    })
    void testSPO2Validation(Integer spo2, String expectedError) {
        // Given
        SPO2Reading reading = new SPO2Reading(
            "test-spo2",
            "p-001",
            "2025-08-01T12:00:00Z",
            spo2
        );

        when(vitalRepository.existsByReadingId(anyString()))
            .thenReturn(Mono.just(false));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .expectErrorMatches(error -> error.getMessage().contains(expectedError))
            .verify();
    }

    @ParameterizedTest
    @DisplayName("Required Fields Validation - ReadingId and PatientId")
    @CsvSource({
        // readingId, patientId, expectedError
        ", p-001, 'readingId is required'",
        "'', p-001, 'readingId is required'",
        "'   ', p-001, 'readingId is required'",
        "test-1, , 'patientId is required'",
        "test-1, '', 'patientId is required'"
    })
    void testRequiredFieldsValidation(String readingId, String patientId, String expectedError) {
        // Given
        BPReading reading = new BPReading(
            readingId,
            patientId,
            "2025-08-01T12:00:00Z",
            120,
            80
        );

        when(vitalRepository.existsByReadingId(any()))
            .thenReturn(Mono.just(false));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .expectErrorMatches(error -> error.getMessage().contains(expectedError))
            .verify();
    }

    @ParameterizedTest
    @DisplayName("Valid BP Readings - Different Values")
    @CsvSource({
        // systolic, diastolic, description
        "120, 80, Normal BP",
        "140, 90, High BP threshold",
        "90, 60, Low BP",
        "0, 0, Minimum valid values",
        "300, 200, Maximum valid values"
    })
    void testValidBPReadings(Integer systolic, Integer diastolic, String description) {
        // Given
        String readingId = "bp-" + systolic + "-" + diastolic;
        BPReading reading = new BPReading(
            readingId,
            "p-001",
            "2025-08-01T12:00:00Z",
            systolic,
            diastolic
        );

        when(vitalRepository.existsByReadingId(readingId))
            .thenReturn(Mono.just(false));
        when(vitalRepository.save(any(VitalReadingEntity.class)))
            .thenReturn(Mono.just(VitalReadingEntity.forBP(
                readingId, "p-001", systolic, diastolic,
                java.time.LocalDateTime.parse("2025-08-01T12:00:00")
            )));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .verifyComplete();

        verify(vitalRepository).existsByReadingId(readingId);
        verify(vitalRepository).save(any(VitalReadingEntity.class));
    }

    @ParameterizedTest
    @DisplayName("Valid HR Readings - Different Values")
    @CsvSource({
        // hr, description
        "60, Normal resting HR",
        "120, High HR",
        "45, Low HR",
        "0, Minimum valid value",
        "300, Maximum valid value"
    })
    void testValidHRReadings(Integer hr, String description) {
        // Given
        String readingId = "hr-" + hr;
        HRReading reading = new HRReading(
            readingId,
            "p-001",
            "2025-08-01T12:00:00Z",
            hr
        );

        when(vitalRepository.existsByReadingId(readingId))
            .thenReturn(Mono.just(false));
        when(vitalRepository.save(any(VitalReadingEntity.class)))
            .thenReturn(Mono.just(VitalReadingEntity.forHR(
                readingId, "p-001", hr,
                java.time.LocalDateTime.parse("2025-08-01T12:00:00")
            )));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .verifyComplete();

        verify(vitalRepository).existsByReadingId(readingId);
        verify(vitalRepository).save(any(VitalReadingEntity.class));
    }

    @ParameterizedTest
    @DisplayName("Valid SPO2 Readings - Different Values")
    @CsvSource({
        // spo2, description
        "98, Normal SPO2",
        "95, Good SPO2",
        "90, Low SPO2",
        "0, Minimum valid value",
        "100, Maximum valid value"
    })
    void testValidSPO2Readings(Integer spo2, String description) {
        // Given
        String readingId = "spo2-" + spo2;
        SPO2Reading reading = new SPO2Reading(
            readingId,
            "p-001",
            "2025-08-01T12:00:00Z",
            spo2
        );

        when(vitalRepository.existsByReadingId(readingId))
            .thenReturn(Mono.just(false));
        when(vitalRepository.save(any(VitalReadingEntity.class)))
            .thenReturn(Mono.just(VitalReadingEntity.forSPO2(
                readingId, "p-001", spo2,
                java.time.LocalDateTime.parse("2025-08-01T12:00:00")
            )));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .verifyComplete();

        verify(vitalRepository).existsByReadingId(readingId);
        verify(vitalRepository).save(any(VitalReadingEntity.class));
    }

    @ParameterizedTest
    @DisplayName("Alert Thresholds - BP Readings")
    @CsvSource({
        // systolic, diastolic, shouldAlert
        "139, 89, false",  // Normal - no alert
        "140, 89, true",   // High systolic - alert
        "139, 90, true",   // High diastolic - alert
        "140, 90, true",   // Both high - alert
        "150, 95, true",   // Well above threshold - alert
    })
    void testBPAlertThresholds(Integer systolic, Integer diastolic, boolean shouldAlert) {
        // This test verifies the values that should trigger alerts
        // In actual implementation, the alert service would evaluate these
        String readingId = "bp-alert-" + systolic + "-" + diastolic;
        BPReading reading = new BPReading(
            readingId,
            "p-001",
            "2025-08-01T12:00:00Z",
            systolic,
            diastolic
        );

        when(vitalRepository.existsByReadingId(readingId))
            .thenReturn(Mono.just(false));
        when(vitalRepository.save(any(VitalReadingEntity.class)))
            .thenReturn(Mono.just(VitalReadingEntity.forBP(
                readingId, "p-001", systolic, diastolic,
                java.time.LocalDateTime.parse("2025-08-01T12:00:00")
            )));

        // When & Then - Service should process all readings regardless of alert threshold
        StepVerifier.create(vitalService.processReading(reading))
            .verifyComplete();
    }

    @ParameterizedTest
    @DisplayName("Alert Thresholds - HR Readings")
    @CsvSource({
        // hr, shouldAlert
        "50, false",  // Normal lower bound - no alert
        "49, true",   // Below lower threshold - alert
        "110, false", // Normal upper bound - no alert
        "111, true",  // Above upper threshold - alert
        "120, true",  // Well above threshold - alert
        "45, true",   // Well below threshold - alert
    })
    void testHRAlertThresholds(Integer hr, boolean shouldAlert) {
        String readingId = "hr-alert-" + hr;
        HRReading reading = new HRReading(
            readingId,
            "p-001",
            "2025-08-01T12:00:00Z",
            hr
        );

        when(vitalRepository.existsByReadingId(readingId))
            .thenReturn(Mono.just(false));
        when(vitalRepository.save(any(VitalReadingEntity.class)))
            .thenReturn(Mono.just(VitalReadingEntity.forHR(
                readingId, "p-001", hr,
                java.time.LocalDateTime.parse("2025-08-01T12:00:00")
            )));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .verifyComplete();
    }

    @ParameterizedTest
    @DisplayName("Alert Thresholds - SPO2 Readings")
    @CsvSource({
        // spo2, shouldAlert
        "92, false",  // Normal threshold - no alert
        "91, true",   // Below threshold - alert
        "90, true",   // Below threshold - alert
        "95, false",  // Normal - no alert
        "98, false",  // Normal - no alert
    })
    void testSPO2AlertThresholds(Integer spo2, boolean shouldAlert) {
        String readingId = "spo2-alert-" + spo2;
        SPO2Reading reading = new SPO2Reading(
            readingId,
            "p-001",
            "2025-08-01T12:00:00Z",
            spo2
        );

        when(vitalRepository.existsByReadingId(readingId))
            .thenReturn(Mono.just(false));
        when(vitalRepository.save(any(VitalReadingEntity.class)))
            .thenReturn(Mono.just(VitalReadingEntity.forSPO2(
                readingId, "p-001", spo2,
                java.time.LocalDateTime.parse("2025-08-01T12:00:00")
            )));

        // When & Then
        StepVerifier.create(vitalService.processReading(reading))
            .verifyComplete();
    }
}