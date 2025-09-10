package com.folautech.vital.repository;

import com.folautech.vital.model.VitalReadingEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface VitalRepository extends R2dbcRepository<VitalReadingEntity, String> {
    
    // Find all readings for a specific patient
    Flux<VitalReadingEntity> findByPatientIdOrderByCapturedAtDesc(String patientId);
    
    // Check if a reading already exists (for idempotency)
    Mono<Boolean> existsByReadingId(String readingId);
    
    // Find readings by type
    Flux<VitalReadingEntity> findByType(String type);
    
    // Custom query to find recent readings for a patient
    @Query("SELECT * FROM vital_readings WHERE patient_id = :patientId ORDER BY captured_at DESC LIMIT :limit")
    Flux<VitalReadingEntity> findRecentReadingsByPatientId(String patientId, int limit);
}