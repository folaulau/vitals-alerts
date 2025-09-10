package com.folautech.alert.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BPReading extends VitalReading {
    private Integer systolic;
    private Integer diastolic;
    
    public BPReading() {
        super();
        setType("BP");
    }

    public BPReading(String readingId, String patientId, String capturedAt, Integer systolic, Integer diastolic) {
        super(readingId, patientId, "BP", capturedAt);
        this.systolic = systolic;
        this.diastolic = diastolic;
    }
}