package com.folautech.vital.controller;

import com.folautech.vital.model.Alert;
import com.folautech.vital.model.VitalReading;
import com.folautech.vital.service.VitalService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/readings")
@Tag(name = "Vital Readings", description = "API for managing patient vital readings")
public class VitalController {
    
    private static final Logger logger = LoggerFactory.getLogger(VitalController.class);
    
    private final VitalService vitalService;
    
    public VitalController(VitalService vitalService) {
        this.vitalService = vitalService;
    }
    
    @PostMapping
    @Operation(summary = "Submit vital readings", 
               description = "Accepts a list of patient vital readings, validates them, stores them, forwards to alert service, and returns created alerts. Use ?transactional=true for all-or-nothing processing.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Readings processed successfully, returning created alerts"),
        @ApiResponse(responseCode = "207", description = "Multi-status response (some readings may have failed)"),
        @ApiResponse(responseCode = "400", description = "Invalid reading data", 
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<ResponseEntity<List<Alert>>> submitReadings(
            @RequestBody List<VitalReading> readings,
            @RequestParam(value = "transactional", defaultValue = "false", required = false) boolean transactional) {
        logger.info("Received {} vital readings (transactional={})", readings.size(), transactional);
        
        Mono<List<Alert>> processingResult = transactional 
            ? vitalService.processReadingsTransactional(readings)
            : vitalService.processReadings(readings);
            
        return processingResult
            .map(alerts -> {
                logger.info("Processed {} readings, created {} alerts", readings.size(), alerts.size());
                return ResponseEntity.ok(alerts);
            })
            .onErrorResume(error -> {
                logger.error("Error processing readings: {}", error.getMessage());
                // Handle specific error cases
                if (error.getMessage() != null && error.getMessage().contains("already exists")) {
                    // Idempotent response for duplicates (when single reading)
                    return Mono.just(ResponseEntity.ok(List.<Alert>of()));
                }
                // Let ResponseStatusException pass through for validation errors
                if (error instanceof org.springframework.web.server.ResponseStatusException) {
                    return Mono.error(error);
                }
                // For other errors, return 500
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.<Alert>of()));
            });
    }
    
    @GetMapping("/readings")
    @Operation(summary = "Get all vital readings", 
               description = "Retrieves all stored vital readings from the database")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Readings retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<ResponseEntity<List<VitalReading>>> getAllReadings() {
        logger.info("Retrieving all vital readings");
        
        return vitalService.getAllReadings()
            .collectList()
            .map(readings -> {
                logger.info("Retrieved {} readings", readings.size());
                return ResponseEntity.ok(readings);
            })
            .onErrorResume(error -> {
                logger.error("Error retrieving readings: {}", error.getMessage());
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.<VitalReading>of()));
            });
    }
    
    @DeleteMapping("/clear")
    @Operation(summary = "Clear all vital readings data", 
               description = "Removes all stored vital readings from the database - useful for testing")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "All data cleared successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<ResponseEntity<String>> clearAllData() {
        logger.info("Clearing all vital readings data");
        
        return vitalService.clearAllData()
            .then(Mono.just(ResponseEntity.ok("All vital readings data cleared")))
            .onErrorResume(error -> {
                logger.error("Error clearing data: {}", error.getMessage());
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error clearing data: " + error.getMessage()));
            });
    }
    
    // Error response DTO for Swagger documentation
    static class ErrorResponse {
        @Schema(description = "Error message", example = "systolic and diastolic are required for BP readings")
        private String message;
        
        @Schema(description = "HTTP status code", example = "400")
        private int status;
        
        @Schema(description = "Timestamp of the error", example = "2025-08-01T12:00:00Z")
        private String timestamp;
        
        // Getters and setters
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }
}