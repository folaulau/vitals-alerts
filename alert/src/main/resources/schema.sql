-- Alerts table to store triggered alerts
DROP TABLE IF EXISTS alerts;

CREATE TABLE IF NOT EXISTS alerts (
    id SERIAL PRIMARY KEY,
    alert_id VARCHAR(50) UNIQUE NOT NULL,
    patient_id VARCHAR(50) NOT NULL,
    reading_id VARCHAR(50) NOT NULL,
    reading_type VARCHAR(10) NOT NULL,
    alert_type VARCHAR(20) NOT NULL,
    threshold_violated VARCHAR(100) NOT NULL,
    reading_value VARCHAR(100) NOT NULL,
    triggered_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_reading_type CHECK (reading_type IN ('BP', 'HR', 'SPO2')),
    CONSTRAINT chk_alert_type CHECK (alert_type IN ('HIGH', 'LOW', 'CRITICAL'))
);

-- Indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_alerts_patient_id ON alerts(patient_id);
CREATE INDEX IF NOT EXISTS idx_alerts_triggered_at ON alerts(triggered_at DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_reading_id ON alerts(reading_id);