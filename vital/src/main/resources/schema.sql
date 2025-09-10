-- Drop table if exists for clean slate (comment out in production)
DROP TABLE IF EXISTS vital_readings;

-- Create vital_readings table
CREATE TABLE IF NOT EXISTS vital_readings (
    reading_id VARCHAR(50) PRIMARY KEY,
    patient_id VARCHAR(50) NOT NULL,
    type VARCHAR(10) NOT NULL,
    systolic INTEGER,
    diastolic INTEGER,
    hr INTEGER,
    spo2 INTEGER,
    captured_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_type CHECK (type IN ('BP', 'HR', 'SPO2'))
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_vital_patient_id ON vital_readings(patient_id);
CREATE INDEX IF NOT EXISTS idx_vital_captured_at ON vital_readings(captured_at DESC);