package com.folautech.alert.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class HRReading extends VitalReading {
    private Integer hr;
    
    public HRReading() {
        super();
        setType("HR");
    }

    public HRReading(String readingId, String patientId, String capturedAt, Integer hr) {
        super(readingId, patientId, "HR", capturedAt);
        this.hr = hr;
    }
}