package com.folautech.alert.controller;

import com.folautech.alert.model.Alert;
import com.folautech.alert.model.VitalReading;
import com.folautech.alert.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@Tag(name = "Alert Management", description = "API for evaluating vital readings and managing alerts")
public class AlertController {
    
    private static final Logger logger = LoggerFactory.getLogger(AlertController.class);
    
    private final AlertService alertService;
    
    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }
    
    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate vital readings", 
               description = "Receives a list of vital readings from the Vital Service and evaluates them against thresholds")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Readings evaluated, returning created alerts"),
        @ApiResponse(responseCode = "400", description = "Invalid reading data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<ResponseEntity<List<Alert>>> evaluateReadings(@RequestBody List<VitalReading> readings) {
        logger.info("Received {} readings for evaluation", readings.size());
        
        return alertService.evaluateReadings(readings)
            .collectList()
            .map(alerts -> {
                logger.info("Created {} alerts from {} readings", alerts.size(), readings.size());
                return ResponseEntity.ok(alerts);
            })
            .onErrorResume(error -> {
                logger.error("Error evaluating readings: {}", error.getMessage());
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of()));
            });
    }
    
    @GetMapping("/alerts")
    @Operation(summary = "Get alerts for patient", 
               description = "Retrieves all alerts for a specific patient, ordered by most recent first")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Alerts retrieved successfully", 
                    content = @Content(schema = @Schema(implementation = Alert.class))),
        @ApiResponse(responseCode = "400", description = "Invalid patient ID"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Flux<Alert> getAlerts(
            @Parameter(description = "Patient ID to retrieve alerts for", required = true)
            @RequestParam String patientId) {
        logger.info("Fetching alerts for patient: {}", patientId);
        
        if (patientId == null || patientId.trim().isEmpty()) {
            logger.error("Invalid patient ID provided");
            return Flux.error(new IllegalArgumentException("Patient ID is required"));
        }
        
        return alertService.getAlertsByPatientId(patientId)
            .doOnComplete(() -> logger.info("Completed fetching alerts for patient: {}", patientId))
            .doOnError(error -> logger.error("Error fetching alerts for patient {}: {}", patientId, error.getMessage()));
    }
    
    @DeleteMapping("/alerts/clear")
    @Operation(summary = "Clear all alerts data", 
               description = "Removes all stored alerts from the database - useful for testing")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "All alerts cleared successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<ResponseEntity<String>> clearAllAlerts() {
        logger.info("Clearing all alerts data");
        
        return alertService.clearAllAlerts()
            .then(Mono.just(ResponseEntity.ok("All alerts data cleared")))
            .onErrorResume(error -> {
                logger.error("Error clearing alerts: {}", error.getMessage());
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error clearing alerts: " + error.getMessage()));
            });
    }
    
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the Alert Service is running")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    public Mono<ResponseEntity<String>> health() {
        return Mono.just(ResponseEntity.ok("Alert Service is running"));
    }
}