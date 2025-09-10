package com.folautech.vital.service;

import com.folautech.vital.model.*;
import com.folautech.vital.model.Alert;
import com.folautech.vital.repository.VitalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class VitalService {
    
    private static final Logger logger = LoggerFactory.getLogger(VitalService.class);
    
    private final VitalRepository vitalRepository;
    private final WebClient webClient;
    
    @Value("${alert.service.url:http://localhost:8082}")
    private String alertServiceUrl;
    
    @Value("${alert.service.timeout.seconds:5}")
    private int alertServiceTimeoutSeconds;
    
    public VitalService(VitalRepository vitalRepository, WebClient.Builder webClientBuilder) {
        this.vitalRepository = vitalRepository;
        this.webClient = webClientBuilder.build();
    }
    
    /**
     * Process a list of vital readings (partial success allowed)
     * @param readings List of vital readings to process
     * @return Mono<List<Alert>> list of alerts created
     */
    public Mono<List<Alert>> processReadings(List<VitalReading> readings) {
        logger.info("Processing {} vital readings", readings.size());
        
        // Process all readings and collect successful ones
        return Flux.fromIterable(readings)
            .flatMap(reading -> {
                logger.debug("Processing reading: type={}, patientId={}, readingId={}", 
                    reading.getType(), reading.getPatientId(), reading.getReadingId());
                return validateReading(reading)
                    .then(checkIdempotency(reading.getReadingId()))
                    .then(saveReading(reading))
                    .thenReturn(reading)
                    .onErrorResume(error -> {
                        logger.error("Error processing reading {}: {}", reading.getReadingId(), error.getMessage());
                        // For batch processing, continue with other readings even if one fails
                        // But for single reading, propagate the error
                        if (readings.size() == 1) {
                            return Mono.error(error);
                        }
                        return Mono.empty();
                    });
            })
            .collectList()
            .flatMap(processedReadings -> {
                // Forward all successfully processed readings to alert service as a batch
                if (!processedReadings.isEmpty()) {
                    return forwardToAlertServiceAndGetAlerts(processedReadings);
                }
                return Mono.just(List.of());
            });
    }
    
    /**
     * Process a list of vital readings with transactional consistency (all-or-nothing)
     * @param readings List of vital readings to process
     * @return Mono<List<Alert>> list of alerts created if all succeed
     */
    @Transactional
    public Mono<List<Alert>> processReadingsTransactional(List<VitalReading> readings) {
        logger.info("Processing {} vital readings transactionally (all-or-nothing)", readings.size());
        
        // First validate ALL readings before saving any
        return Flux.fromIterable(readings)
            .flatMap(reading -> {
                logger.debug("Validating reading: type={}, patientId={}, readingId={}", 
                    reading.getType(), reading.getPatientId(), reading.getReadingId());
                return validateReading(reading)
                    .then(checkIdempotency(reading.getReadingId()))
                    .thenReturn(reading);
            })
            .collectList()
            .flatMap(validatedReadings -> {
                // If all validations pass, save all readings
                logger.info("All {} readings validated, saving to database", validatedReadings.size());
                return Flux.fromIterable(validatedReadings)
                    .flatMap(this::saveReading)
                    .then(Mono.just(validatedReadings));
            })
            .flatMap(savedReadings -> {
                // Forward all readings to alert service
                logger.info("All {} readings saved, forwarding to alert service", savedReadings.size());
                return forwardToAlertServiceAndGetAlerts(savedReadings);
            })
            .onErrorResume(error -> {
                logger.error("Transaction failed, rolling back all changes: {}", error.getMessage());
                // Transaction will automatically rollback on error
                return Mono.error(error);
            });
    }
    
    public Mono<Void> processReading(VitalReading reading) {
        // Validate the reading based on type
        return validateReading(reading)
            // Check for idempotency
            .then(checkIdempotency(reading.getReadingId()))
            // Convert to entity and save
            .then(saveReading(reading))
            // Forward to alert service asynchronously
            .then(forwardToAlertService(reading))
            .then();
    }
    
    private Mono<Void> validateReading(VitalReading reading) {
        return Mono.fromRunnable(() -> {
            if (reading.getReadingId() == null || reading.getReadingId().trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "readingId is required");
            }
            if (reading.getPatientId() == null || reading.getPatientId().trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
            }
            if (reading.getCapturedAt() == null || reading.getCapturedAt().trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "capturedAt is required");
            }
            
            // Type-specific validation
            switch (reading.getType()) {
                case "BP":
                    if (reading instanceof BPReading bp) {
                        if (bp.getSystolic() == null || bp.getDiastolic() == null) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                "systolic and diastolic are required for BP readings");
                        }
                        if (bp.getSystolic() < 0 || bp.getSystolic() > 300) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                "systolic must be between 0 and 300");
                        }
                        if (bp.getDiastolic() < 0 || bp.getDiastolic() > 200) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                "diastolic must be between 0 and 200");
                        }
                    }
                    break;
                case "HR":
                    if (reading instanceof HRReading hr) {
                        if (hr.getHr() == null) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                "hr is required for HR readings");
                        }
                        if (hr.getHr() < 0 || hr.getHr() > 300) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                "hr must be between 0 and 300");
                        }
                    }
                    break;
                case "SPO2":
                    if (reading instanceof SPO2Reading spo2) {
                        if (spo2.getSpo2() == null) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                "spo2 is required for SPO2 readings");
                        }
                        if (spo2.getSpo2() < 0 || spo2.getSpo2() > 100) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                "spo2 must be between 0 and 100");
                        }
                    }
                    break;
                default:
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        "Invalid type: " + reading.getType());
            }
        });
    }
    
    private Mono<Void> checkIdempotency(String readingId) {
        return vitalRepository.existsByReadingId(readingId)
            .flatMap(exists -> {
                if (exists) {
                    logger.info("Reading with ID {} already exists, ignoring duplicate", readingId);
                    return Mono.error(new RuntimeException("Reading already exists"));
                }
                return Mono.just(readingId);
            })
            .then();
    }
    
    private Mono<VitalReadingEntity> saveReading(VitalReading reading) {
        VitalReadingEntity entity = convertToEntity(reading);
        return vitalRepository.save(entity)
            .doOnSuccess(saved -> logger.info("Saved vital reading: {}", saved.getReadingId()))
            .doOnError(error -> logger.error("Error saving vital reading: {}", error.getMessage()));
    }
    
    private VitalReadingEntity convertToEntity(VitalReading reading) {
        LocalDateTime capturedAt = LocalDateTime.parse(reading.getCapturedAt(), 
            DateTimeFormatter.ISO_DATE_TIME);
        
        switch (reading.getType()) {
            case "BP":
                BPReading bp = (BPReading) reading;
                return VitalReadingEntity.forBP(
                    reading.getReadingId(),
                    reading.getPatientId(),
                    bp.getSystolic(),
                    bp.getDiastolic(),
                    capturedAt
                );
            case "HR":
                HRReading hr = (HRReading) reading;
                return VitalReadingEntity.forHR(
                    reading.getReadingId(),
                    reading.getPatientId(),
                    hr.getHr(),
                    capturedAt
                );
            case "SPO2":
                SPO2Reading spo2 = (SPO2Reading) reading;
                return VitalReadingEntity.forSPO2(
                    reading.getReadingId(),
                    reading.getPatientId(),
                    spo2.getSpo2(),
                    capturedAt
                );
            default:
                throw new IllegalArgumentException("Unknown reading type: " + reading.getType());
        }
    }
    
    private Mono<Void> forwardToAlertService(VitalReading reading) {
        // For single reading, wrap in a list
        return forwardToAlertService(List.of(reading));
    }
    
    private Mono<Void> forwardToAlertService(List<VitalReading> readings) {
        if (readings.isEmpty()) {
            return Mono.empty();
        }
        
        String readingIds = readings.stream()
            .map(VitalReading::getReadingId)
            .filter(id -> id != null)
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
            
        return webClient
            .post()
            .uri(alertServiceUrl + "/evaluate")
            .bodyValue(readings)  // Send as list
            .retrieve()
            .toBodilessEntity()
            .timeout(java.time.Duration.ofSeconds(alertServiceTimeoutSeconds))
            .doOnSuccess(response -> logger.info("Successfully forwarded {} readings to alert service: {}", 
                readings.size(), readingIds))
            .doOnError(error -> logger.error("Failed to forward {} readings to alert service: {}", 
                readings.size(), error.getMessage()))
            .onErrorResume(error -> {
                // Log but don't fail the main request
                logger.error("Alert service communication failed, but continuing: {}", error.getMessage());
                return Mono.empty();
            })
            .then();
    }
    
    private Mono<List<Alert>> forwardToAlertServiceAndGetAlerts(List<VitalReading> readings) {
        if (readings.isEmpty()) {
            return Mono.just(List.of());
        }
        
        String readingIds = readings.stream()
            .map(VitalReading::getReadingId)
            .filter(id -> id != null)
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
            
        return webClient
            .post()
            .uri(alertServiceUrl + "/evaluate")
            .bodyValue(readings)  // Send as list
            .retrieve()
            .bodyToMono(Alert[].class)
            .map(alerts -> List.of(alerts))
            .timeout(java.time.Duration.ofSeconds(alertServiceTimeoutSeconds))
            .doOnSuccess(alerts -> logger.info("Successfully forwarded {} readings to alert service, received {} alerts", 
                readings.size(), alerts.size()))
            .doOnError(error -> logger.error("Failed to forward {} readings to alert service: {}", 
                readings.size(), error.getMessage()))
            .onErrorResume(error -> {
                // Log but don't fail the main request
                logger.error("Alert service communication failed, but continuing: {}", error.getMessage());
                return Mono.just(List.of());
            });
    }
    
    public Mono<Void> clearAllData() {
        logger.info("Clearing all vital readings from database");
        return vitalRepository.deleteAll()
            .doOnSuccess(result -> logger.info("Successfully cleared all vital readings"))
            .doOnError(error -> logger.error("Error clearing vital readings: {}", error.getMessage()));
    }
    
    public Flux<VitalReading> getAllReadings() {
        return vitalRepository.findAll()
            .map(this::convertEntityToReading)
            .doOnComplete(() -> logger.info("Retrieved all vital readings"))
            .doOnError(error -> logger.error("Error retrieving vital readings: {}", error.getMessage()));
    }
    
    private VitalReading convertEntityToReading(VitalReadingEntity entity) {
        String capturedAt = entity.getCapturedAt().format(DateTimeFormatter.ISO_DATE_TIME);
        
        switch (entity.getType()) {
            case "BP":
                return new BPReading(
                    entity.getReadingId(),
                    entity.getPatientId(),
                    capturedAt,
                    entity.getSystolic(),
                    entity.getDiastolic()
                );
            case "HR":
                return new HRReading(
                    entity.getReadingId(),
                    entity.getPatientId(),
                    capturedAt,
                    entity.getHr()
                );
            case "SPO2":
                return new SPO2Reading(
                    entity.getReadingId(),
                    entity.getPatientId(),
                    capturedAt,
                    entity.getSpo2()
                );
            default:
                throw new IllegalArgumentException("Unknown reading type: " + entity.getType());
        }
    }
}