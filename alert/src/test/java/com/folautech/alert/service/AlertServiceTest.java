package com.folautech.alert.service;

import com.folautech.alert.model.*;
import com.folautech.alert.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private AlertService alertService;

    private String readingId;
    private String patientId;
    private String capturedAt;

    @BeforeEach
    void setUp() {
        readingId = UUID.randomUUID().toString();
        patientId = "p-001";
        capturedAt = LocalDateTime.now().toString();
    }

    @Test
    @DisplayName("Should create CRITICAL alert for high blood pressure (both systolic and diastolic high)")
    void testBPReadingCriticalAlert() {
        BPReading reading = new BPReading(readingId, patientId, capturedAt, 150, 95);
        
        when(alertRepository.existsByReadingId(readingId)).thenReturn(Mono.just(false));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setId(1L);
            return Mono.just(alert);
        });

        StepVerifier.create(alertService.evaluateReading(reading))
                .expectNextMatches(alert -> {
                    return alert.getAlertType().equals("CRITICAL") &&
                           alert.getThresholdViolated().contains("Systolic >= 140 AND Diastolic >= 90") &&
                           alert.getReadingValue().equals("150/95") &&
                           alert.getPatientId().equals(patientId) &&
                           alert.getReadingId().equals(readingId);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should create HIGH alert for high systolic only")
    void testBPReadingHighSystolicAlert() {
        BPReading reading = new BPReading(readingId, patientId, capturedAt, 145, 85);
        
        when(alertRepository.existsByReadingId(readingId)).thenReturn(Mono.just(false));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setId(1L);
            return Mono.just(alert);
        });

        StepVerifier.create(alertService.evaluateReading(reading))
                .expectNextMatches(alert -> {
                    return alert.getAlertType().equals("HIGH") &&
                           alert.getThresholdViolated().contains("Systolic >= 140") &&
                           alert.getReadingValue().equals("145/85") &&
                           alert.getPatientId().equals(patientId) &&
                           alert.getReadingId().equals(readingId);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should create HIGH alert for high diastolic only")
    void testBPReadingHighDiastolicAlert() {
        BPReading reading = new BPReading(readingId, patientId, capturedAt, 135, 92);
        
        when(alertRepository.existsByReadingId(readingId)).thenReturn(Mono.just(false));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setId(1L);
            return Mono.just(alert);
        });

        StepVerifier.create(alertService.evaluateReading(reading))
                .expectNextMatches(alert -> {
                    return alert.getAlertType().equals("HIGH") &&
                           alert.getThresholdViolated().contains("Diastolic >= 90") &&
                           alert.getReadingValue().equals("135/92") &&
                           alert.getPatientId().equals(patientId) &&
                           alert.getReadingId().equals(readingId);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should not create alert for normal BP reading")
    void testBPReadingNoAlert() {
        BPReading reading = new BPReading(readingId, patientId, capturedAt, 120, 80);
        
        when(alertRepository.existsByReadingId(readingId)).thenReturn(Mono.just(false));

        StepVerifier.create(alertService.evaluateReading(reading))
                .verifyComplete();

        verify(alertRepository, never()).save(any(Alert.class));
    }

    @ParameterizedTest
    @DisplayName("Heart Rate alert thresholds")
    @CsvSource({
        "45, LOW, 'Heart Rate < 50'",
        "49, LOW, 'Heart Rate < 50'",
        "111, HIGH, 'Heart Rate > 110'",
        "120, HIGH, 'Heart Rate > 110'"
    })
    void testHRReadingAlerts(int hr, String expectedAlertType, String expectedThreshold) {
        HRReading reading = new HRReading(readingId, patientId, capturedAt, hr);
        
        when(alertRepository.existsByReadingId(readingId)).thenReturn(Mono.just(false));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setId(1L);
            return Mono.just(alert);
        });

        StepVerifier.create(alertService.evaluateReading(reading))
                .expectNextMatches(alert -> {
                    return alert.getAlertType().toString().equals(expectedAlertType) &&
                           alert.getThresholdViolated().equals(expectedThreshold) &&
                           alert.getReadingValue().equals(String.valueOf(hr)) &&
                           alert.getPatientId().equals(patientId) &&
                           alert.getReadingId().equals(readingId);
                })
                .verifyComplete();
    }

    @ParameterizedTest
    @DisplayName("Heart Rate normal readings - no alerts")
    @CsvSource({
        "50",
        "60",
        "75",
        "90",
        "110"
    })
    void testHRReadingNoAlert(int hr) {
        HRReading reading = new HRReading(readingId, patientId, capturedAt, hr);
        
        when(alertRepository.existsByReadingId(readingId)).thenReturn(Mono.just(false));

        StepVerifier.create(alertService.evaluateReading(reading))
                .verifyComplete();

        verify(alertRepository, never()).save(any(Alert.class));
    }

    @ParameterizedTest
    @DisplayName("SPO2 alert thresholds")
    @CsvSource({
        "88, CRITICAL, 'SpO2 < 92'",
        "89, CRITICAL, 'SpO2 < 92'",
        "90, LOW, 'SpO2 < 92'",
        "91, LOW, 'SpO2 < 92'"
    })
    void testSPO2ReadingAlerts(int spo2, String expectedAlertType, String expectedThreshold) {
        SPO2Reading reading = new SPO2Reading(readingId, patientId, capturedAt, spo2);
        
        when(alertRepository.existsByReadingId(readingId)).thenReturn(Mono.just(false));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setId(1L);
            return Mono.just(alert);
        });

        StepVerifier.create(alertService.evaluateReading(reading))
                .expectNextMatches(alert -> {
                    return alert.getAlertType().toString().equals(expectedAlertType) &&
                           alert.getThresholdViolated().equals(expectedThreshold) &&
                           alert.getReadingValue().equals(String.valueOf(spo2)) &&
                           alert.getPatientId().equals(patientId) &&
                           alert.getReadingId().equals(readingId);
                })
                .verifyComplete();
    }

    @ParameterizedTest
    @DisplayName("SPO2 normal readings - no alerts")
    @CsvSource({
        "92",
        "95",
        "98",
        "100"
    })
    void testSPO2ReadingNoAlert(int spo2) {
        SPO2Reading reading = new SPO2Reading(readingId, patientId, capturedAt, spo2);
        
        when(alertRepository.existsByReadingId(readingId)).thenReturn(Mono.just(false));

        StepVerifier.create(alertService.evaluateReading(reading))
                .verifyComplete();

        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    @DisplayName("Should handle idempotency - not create duplicate alert for same reading")
    void testIdempotency() {
        BPReading reading = new BPReading(readingId, patientId, capturedAt, 150, 95);
        
        when(alertRepository.existsByReadingId(readingId)).thenReturn(Mono.just(true));

        StepVerifier.create(alertService.evaluateReading(reading))
                .verifyComplete();

        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    @DisplayName("Should handle null values in BP reading")
    void testBPReadingWithNullValues() {
        BPReading reading = new BPReading(readingId, patientId, capturedAt, null, 80);
        
        when(alertRepository.existsByReadingId(readingId)).thenReturn(Mono.just(false));

        StepVerifier.create(alertService.evaluateReading(reading))
                .verifyComplete();

        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    @DisplayName("Should handle null values in HR reading")
    void testHRReadingWithNullValues() {
        HRReading reading = new HRReading(readingId, patientId, capturedAt, null);
        
        when(alertRepository.existsByReadingId(readingId)).thenReturn(Mono.just(false));

        StepVerifier.create(alertService.evaluateReading(reading))
                .verifyComplete();

        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    @DisplayName("Should handle null values in SPO2 reading")
    void testSPO2ReadingWithNullValues() {
        SPO2Reading reading = new SPO2Reading(readingId, patientId, capturedAt, null);
        
        when(alertRepository.existsByReadingId(readingId)).thenReturn(Mono.just(false));

        StepVerifier.create(alertService.evaluateReading(reading))
                .verifyComplete();

        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    @DisplayName("Should fetch alerts for patient ordered by triggered date")
    void testGetAlertsByPatientId() {
        Alert alert1 = new Alert("alert-1", patientId, "reading-1", "BP", 
                AlertType.HIGH, "Systolic >= 140", "145/85", LocalDateTime.now().minusHours(2));
        Alert alert2 = new Alert("alert-2", patientId, "reading-2", "HR", 
                AlertType.LOW, "Heart Rate < 50", "45", LocalDateTime.now().minusHours(1));
        
        when(alertRepository.findByPatientIdOrderByAlertId(patientId))
                .thenReturn(Flux.just(alert1, alert2));

        StepVerifier.create(alertService.getAlertsByPatientId(patientId))
                .expectNext(alert1)
                .expectNext(alert2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle error when saving alert")
    void testErrorHandlingWhenSavingAlert() {
        BPReading reading = new BPReading(readingId, patientId, capturedAt, 150, 95);
        
        when(alertRepository.existsByReadingId(readingId)).thenReturn(Mono.just(false));
        when(alertRepository.save(any(Alert.class))).thenReturn(Mono.error(new RuntimeException("Database error")));

        StepVerifier.create(alertService.evaluateReading(reading))
                .verifyError(RuntimeException.class);
    }
}