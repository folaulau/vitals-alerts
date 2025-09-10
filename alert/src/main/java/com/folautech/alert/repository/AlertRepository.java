package com.folautech.alert.repository;

import com.folautech.alert.model.Alert;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface AlertRepository extends ReactiveCrudRepository<Alert, Long> {
    
    Flux<Alert> findByPatientIdOrderByTriggeredAtDesc(String patientId);
    
    Flux<Alert> findByPatientIdOrderByAlertId(String patientId);
    
    Mono<Boolean> existsByReadingId(String readingId);
    
    @Query("SELECT * FROM alerts WHERE patient_id = :patientId AND triggered_at >= :startTime ORDER BY triggered_at DESC")
    Flux<Alert> findByPatientIdAndTriggeredAtAfter(String patientId, java.time.LocalDateTime startTime);
}