package com.folautech.vital.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("vital_readings")
@Schema(description = "Vital reading entity stored in database")
public class VitalReadingEntity implements Persistable<String> {
    
    @Transient
    @Builder.Default
    private boolean isNew = true;
    
    @Id
    @Column("reading_id")
    @Schema(description = "Unique identifier for the reading", example = "11111111-1111-1111-1111-111111111111")
    private String readingId;
    
    @Column("patient_id")
    @Schema(description = "Patient identifier", example = "p-001")
    private String patientId;
    
    @Column("type")
    @Schema(description = "Type of vital reading", allowableValues = {"BP", "HR", "SPO2"})
    private String type;
    
    @Column("systolic")
    @Schema(description = "Systolic blood pressure (for BP readings)", example = "150")
    private Integer systolic;
    
    @Column("diastolic")
    @Schema(description = "Diastolic blood pressure (for BP readings)", example = "95")
    private Integer diastolic;
    
    @Column("hr")
    @Schema(description = "Heart rate in beats per minute (for HR readings)", example = "120")
    private Integer hr;
    
    @Column("spo2")
    @Schema(description = "Blood oxygen saturation percentage (for SPO2 readings)", example = "90")
    private Integer spo2;
    
    @Column("captured_at")
    @Schema(description = "Timestamp when the reading was captured", example = "2025-08-01T12:00:00Z")
    private LocalDateTime capturedAt;
    
    @Column("created_at")
    @Schema(description = "Timestamp when the record was created in database")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // Factory methods for different reading types
    public static VitalReadingEntity forBP(String readingId, String patientId, Integer systolic, Integer diastolic, LocalDateTime capturedAt) {
        return VitalReadingEntity.builder()
                .readingId(readingId)
                .patientId(patientId)
                .type("BP")
                .systolic(systolic)
                .diastolic(diastolic)
                .capturedAt(capturedAt)
                .build();
    }

    public static VitalReadingEntity forHR(String readingId, String patientId, Integer hr, LocalDateTime capturedAt) {
        return VitalReadingEntity.builder()
                .readingId(readingId)
                .patientId(patientId)
                .type("HR")
                .hr(hr)
                .capturedAt(capturedAt)
                .build();
    }

    public static VitalReadingEntity forSPO2(String readingId, String patientId, Integer spo2, LocalDateTime capturedAt) {
        return VitalReadingEntity.builder()
                .readingId(readingId)
                .patientId(patientId)
                .type("SPO2")
                .spo2(spo2)
                .capturedAt(capturedAt)
                .build();
    }
    
    // Persistable interface methods
    @Override
    public String getId() {
        return readingId;
    }
    
    @Override
    public boolean isNew() {
        return isNew || readingId == null;
    }
}