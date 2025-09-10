package com.folautech.alert.service;

import com.folautech.alert.model.*;
import com.folautech.alert.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class AlertService {
    
    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);
    
    // Threshold constants
    private static final int BP_SYSTOLIC_HIGH = 140;
    private static final int BP_DIASTOLIC_HIGH = 90;
    private static final int HR_LOW = 50;
    private static final int HR_HIGH = 110;
    private static final int SPO2_LOW = 92;
    
    private final AlertRepository alertRepository;
    
    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }
    
    /**
     * Evaluate a list of vital readings
     * @param readings List of vital readings to evaluate
     * @return Flux<Alert> of created alerts
     */
    public Flux<Alert> evaluateReadings(List<VitalReading> readings) {
        logger.info("Evaluating {} vital readings", readings.size());
        
        return Flux.fromIterable(readings)
            .flatMap(reading -> {
                logger.debug("Evaluating reading: type={}, patientId={}, readingId={}", 
                    reading.getType(), reading.getPatientId(), reading.getReadingId());
                return evaluateReading(reading)
                    .onErrorResume(error -> {
                        logger.error("Error evaluating reading {}: {}", reading.getReadingId(), error.getMessage());
                        // Continue processing other readings even if one fails
                        return Mono.empty();
                    });
            })
            .sort((a, b) -> a.getAlertId().compareTo(b.getAlertId()));
    }
    
    public Mono<Alert> evaluateReading(VitalReading reading) {
        logger.info("Evaluating reading: type={}, patientId={}, readingId={}", 
            reading.getType(), reading.getPatientId(), reading.getReadingId());
        
        // Check if alert already exists for this reading (idempotency)
        return alertRepository.existsByReadingId(reading.getReadingId())
            .flatMap(exists -> {
                if (exists) {
                    logger.info("Alert already exists for reading: {}", reading.getReadingId());
                    return Mono.<Alert>empty();
                }
                return evaluateAndCreateAlert(reading);
            });
    }
    
    private Mono<Alert> evaluateAndCreateAlert(VitalReading reading) {
        Alert alert = null;
        String type = reading.getType();
        
        if (type == null) {
            logger.warn("Reading type is null for reading: {}", reading.getReadingId());
            return Mono.empty();
        }
        
        switch (type) {
            case "BP":
                alert = evaluateBPReading((BPReading) reading);
                break;
            case "HR":
                alert = evaluateHRReading((HRReading) reading);
                break;
            case "SPO2":
                alert = evaluateSPO2Reading((SPO2Reading) reading);
                break;
            default:
                logger.warn("Unknown reading type: {}", type);
                return Mono.empty();
        }
        
        if (alert != null) {
            logger.info("Alert triggered for reading: {} - {}", reading.getReadingId(), alert.getThresholdViolated());
            return alertRepository.save(alert)
                .doOnSuccess(saved -> logger.info("Alert saved: {}", saved.getAlertId()))
                .doOnError(error -> logger.error("Error saving alert: {}", error.getMessage()));
        }
        
        logger.debug("No alert triggered for reading: {}", reading.getReadingId());
        return Mono.empty();
    }
    
    private Alert evaluateBPReading(BPReading reading) {
        Integer systolic = reading.getSystolic();
        Integer diastolic = reading.getDiastolic();
        
        if (systolic == null || diastolic == null) {
            return null;
        }
        
        String thresholdViolated = null;
        AlertType alertType = null;
        
        if (systolic >= BP_SYSTOLIC_HIGH && diastolic >= BP_DIASTOLIC_HIGH) {
            thresholdViolated = String.format("Systolic >= %d AND Diastolic >= %d", BP_SYSTOLIC_HIGH, BP_DIASTOLIC_HIGH);
            alertType = AlertType.CRITICAL;
        } else if (systolic >= BP_SYSTOLIC_HIGH) {
            thresholdViolated = String.format("Systolic >= %d", BP_SYSTOLIC_HIGH);
            alertType = AlertType.HIGH;
        } else if (diastolic >= BP_DIASTOLIC_HIGH) {
            thresholdViolated = String.format("Diastolic >= %d", BP_DIASTOLIC_HIGH);
            alertType = AlertType.HIGH;
        }
        
        if (thresholdViolated != null) {
            return new Alert(
                UUID.randomUUID().toString(),
                reading.getPatientId(),
                reading.getReadingId(),
                "BP",
                alertType,
                thresholdViolated,
                String.format("%d/%d", systolic, diastolic),
                parseDateTime(reading.getCapturedAt())
            );
        }
        
        return null;
    }
    
    private Alert evaluateHRReading(HRReading reading) {
        Integer hr = reading.getHr();
        
        if (hr == null) {
            return null;
        }
        
        String thresholdViolated = null;
        AlertType alertType = null;
        
        if (hr < HR_LOW) {
            thresholdViolated = String.format("Heart Rate < %d", HR_LOW);
            alertType = AlertType.LOW;
        } else if (hr > HR_HIGH) {
            thresholdViolated = String.format("Heart Rate > %d", HR_HIGH);
            alertType = AlertType.HIGH;
        }
        
        if (thresholdViolated != null) {
            return new Alert(
                UUID.randomUUID().toString(),
                reading.getPatientId(),
                reading.getReadingId(),
                "HR",
                alertType,
                thresholdViolated,
                String.valueOf(hr),
                parseDateTime(reading.getCapturedAt())
            );
        }
        
        return null;
    }
    
    private Alert evaluateSPO2Reading(SPO2Reading reading) {
        Integer spo2 = reading.getSpo2();
        
        if (spo2 == null) {
            return null;
        }
        
        if (spo2 < SPO2_LOW) {
            String thresholdViolated = String.format("SpO2 < %d", SPO2_LOW);
            AlertType alertType = spo2 < 90 ? AlertType.CRITICAL : AlertType.LOW;
            
            return new Alert(
                UUID.randomUUID().toString(),
                reading.getPatientId(),
                reading.getReadingId(),
                "SPO2",
                alertType,
                thresholdViolated,
                String.valueOf(spo2),
                parseDateTime(reading.getCapturedAt())
            );
        }
        
        return null;
    }
    
    public Flux<Alert> getAlertsByPatientId(String patientId) {
        logger.info("Fetching alerts for patient: {}", patientId);
        return alertRepository.findByPatientIdOrderByAlertId(patientId);
    }
    
    public Mono<Void> clearAllAlerts() {
        logger.info("Clearing all alerts from database");
        return alertRepository.deleteAll()
            .doOnSuccess(result -> logger.info("Successfully cleared all alerts"))
            .doOnError(error -> logger.error("Error clearing alerts: {}", error.getMessage()));
    }
    
    private LocalDateTime parseDateTime(String dateTimeStr) {
        return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME);
    }
}