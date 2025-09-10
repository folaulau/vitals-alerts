package com.folautech.vital.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Alert {
    private Long id;
    private String alertId;
    private String patientId;
    private String readingId;
    private String readingType;
    private String alertType;
    private String thresholdViolated;
    private String readingValue;
    private LocalDateTime triggeredAt;
    private LocalDateTime createdAt;
}