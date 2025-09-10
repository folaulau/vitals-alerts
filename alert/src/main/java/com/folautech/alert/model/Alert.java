package com.folautech.alert.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("alerts")
public class Alert {
    @Id
    private Long id;
    
    @Column("alert_id")
    private String alertId;
    
    @Column("patient_id")
    private String patientId;
    
    @Column("reading_id")
    private String readingId;
    
    @Column("reading_type")
    private String readingType;
    
    @Column("alert_type")
    private String alertType;
    
    @Column("threshold_violated")
    private String thresholdViolated;
    
    @Column("reading_value")
    private String readingValue;
    
    @Column("triggered_at")
    private LocalDateTime triggeredAt;
    
    @Column("created_at")
    private LocalDateTime createdAt;

    public Alert(String alertId, String patientId, String readingId, String readingType, 
                AlertType alertType, String thresholdViolated, String readingValue, LocalDateTime triggeredAt) {
        this.alertId = alertId;
        this.patientId = patientId;
        this.readingId = readingId;
        this.readingType = readingType;
        this.alertType = alertType.name();
        this.thresholdViolated = thresholdViolated;
        this.readingValue = readingValue;
        this.triggeredAt = triggeredAt;
        this.createdAt = LocalDateTime.now();
    }
}