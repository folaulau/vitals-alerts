package com.folautech.alert.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = BPReading.class, name = "BP"),
    @JsonSubTypes.Type(value = HRReading.class, name = "HR"),
    @JsonSubTypes.Type(value = SPO2Reading.class, name = "SPO2")
})
public abstract class VitalReading {
    private String readingId;
    private String patientId;
    private String type;
    private String capturedAt;
}