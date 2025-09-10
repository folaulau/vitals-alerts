package com.folautech.alert.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SPO2Reading extends VitalReading {
    private Integer spo2;
    
    public SPO2Reading() {
        super();
        setType("SPO2");
    }

    public SPO2Reading(String readingId, String patientId, String capturedAt, Integer spo2) {
        super(readingId, patientId, "SPO2", capturedAt);
        this.spo2 = spo2;
    }
}