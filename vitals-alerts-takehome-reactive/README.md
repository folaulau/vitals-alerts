# Vitals → Alerts (Reactive) — Take-Home Assignment

## Overview
Two reactive Spring Boot **3.5.4** microservices (Java **21**) using **Spring WebFlux** and **Project Reactor**:

1. **Vitals Service** — Accepts patient vital readings, stores them in memory, and forwards them asynchronously to Alerts Service.
2. **Alerts Service** — Evaluates readings against threshold rules and stores generated alerts in memory.

Target duration: **1–2 hours**.

## Tech Requirements
- Java 21
- Spring Boot 3.5.4
- **Spring WebFlux** (use Reactor types: `Mono`, `Flux`)
- `WebClient` for service-to-service calls
- In-memory storage (e.g., `ConcurrentHashMap`)

## Endpoints

### Vitals Service (Port 8081)
**POST** `/readings` → returns `Mono<Void>` or `Mono<ResponseEntity<...>>`
- Accept JSON for a vital reading.
- Validate required fields based on `type`.
- Store in memory keyed by `readingId`.
- Idempotency: ignore duplicates (`readingId`).
- Asynchronously POST to `http://localhost:8082/evaluate` using `WebClient`.

### Alerts Service (Port 8082)
**POST** `/evaluate` → returns `Mono<Void>` or `Mono<ResponseEntity<...>>`
- Apply thresholds:
  - BP: `systolic ≥ 140` or `diastolic ≥ 90`
  - HR: `< 50` or `> 110`
  - SPO2: `< 92`
- If exceeded, create `OPEN` alert in memory.
- Ignore duplicates by `readingId`.

**GET** `/alerts?patientId=...` → returns `Flux<Alert>`
- Return alerts for that patient, newest first.

## Mock Data
See `mock-data.json` for example payloads you can POST to `/readings` on Vitals.
Expected: first four create alerts; the last does not.

## Running Locally
1) Start Alerts Service
```bash
cd alerts-service
./mvnw spring-boot:run
```
2) Start Vitals Service
```bash
cd vitals-service
./mvnw spring-boot:run
```

## Example Requests

**Create Reading (Vitals)**
```bash
curl -X POST http://localhost:8081/readings   -H "Content-Type: application/json"   -d '{
    "readingId":"11111111-1111-1111-1111-111111111111",
    "patientId":"p-001",
    "type":"BP",
    "systolic":150,
    "diastolic":95,
    "capturedAt":"2025-08-01T12:00:00Z"
  }'
```

**List Alerts**
```bash
curl "http://localhost:8082/alerts?patientId=p-001"
```

## Submission
- Push code to a public GitHub repo.
- Include this README and any notes in `/NOTES.md` if needed.
