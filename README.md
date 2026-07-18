# Gamified Tracker

A microservices-based activity tracker with gamification. Log activities, earn XP, and level up — built with Spring Boot, Spring Cloud, and PostgreSQL.

## How it works

```
          Client
            │
     ┌──────▼───────┐
     │ API Gateway  │  :8080  — JWT auth, routing
     └──────┬───────┘
     ┌───────┴────────┐
     ▼                ▼
┌──────────┐   ┌──────────────┐
│ Activity │──►│ Gamification │   (async via RabbitMQ)
│  :8081   │   │    :8082     │
└────┬─────┘   └──────┬───────┘
     └────────┬───────┘
     ┌────────▼────────┐
     │  PostgreSQL     │  :5433
     └─────────────────┘

  Eureka service discovery :8761
  
  ──────────────────── OBSERVABILITY ────────────────────────
  Prometheus :9090  ── scrapes ──►  all services (/actuator/prometheus)
  Grafana    :3000  ── queries ──►  Prometheus
  Zipkin     :9411  ◄── traces ──   all services
```

- **API Gateway** — single entry point; handles JWT auth and routes to services.
- **Activity Service** — manages activities and logs activity sessions (computes XP).
- **Gamification Service** — tracks levels, thresholds, and awards points.
- **Eureka Server** — service registry/discovery.
- **Prometheus** — scrapes `/actuator/prometheus` from every service and stores metrics.
- **Grafana** — pre-provisioned dashboard (JVM memory, CPU, uptime, restarts) backed by Prometheus.
- **Zipkin** — collects and visualizes distributed traces across service calls.
- **RabbitMQ** — message broker for the outbox relay; decouples `activity-service` from `gamification-service` via async events.

## Tech stack

Java 17 · Spring Boot 3.5 · Spring Cloud 2025 (Eureka) · RabbitMQ (Spring AMQP) · Spring Security + JWT · PostgreSQL 15 · Maven · Docker Compose · Prometheus · Grafana · Zipkin · Micrometer

## Quick start

**Prerequisites:** Docker + Docker Compose.

```bash
# 1. Configure environment (defaults are fine for local dev)
cp .env.example .env

# 2. Build and run everything
docker-compose up --build
```

That's it — all services, Eureka, PostgreSQL, RabbitMQ, Redis, Prometheus, Grafana, and Zipkin start together.

## Ports

| Service              | Port |
|----------------------|------|
| API Gateway          | 8080 |
| Activity Service     | 8081 |
| Gamification Service | 8082 |
| Eureka dashboard     | 8761 |
| PostgreSQL           | 5433 |
| Prometheus             | 9090 |
| Grafana                | 3000 |
| Zipkin                 | 9411 |
| RabbitMQ               | 5672 |
| Redis                  | 6379 |

## Observability

### Metrics — Prometheus + Grafana

Every service exposes metrics at `/actuator/prometheus` via Micrometer. Prometheus scrapes all four services (`eureka-server`, `api-gateway`, `activity-service`, `gamification-service`) every 15s.

- Prometheus targets: [http://localhost:9090/targets](http://localhost:9090/targets)
- Grafana dashboards: [http://localhost:3000](http://localhost:3000) (default login `admin` / `admin`)

The Grafana instance auto-provisions the Prometheus datasource and a **Spring Boot Micrometer** dashboard (JVM heap/non-heap memory, CPU, load, threads, uptime, restart detection) — no manual setup needed. Filter by `Application` and `Instance` at the top of the dashboard.

### Tracing — Zipkin

Every service sends traces to Zipkin automatically. To follow a request across Gateway → Activity → Gamification:

- Zipkin UI: [http://localhost:9411](http://localhost:9411)
- Search by service name (e.g. `api-gateway`) or trace duration to find slow/failed requests spanning multiple services.

## Try it

```bash
# Register (returns a JWT, carrying role + userId claims)
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Ada","lastName":"L","email":"ada@example.com","password":"secret","role":"USER"}'

# List activities (use the token from register)
curl http://localhost:8080/api/activity -H "Authorization: Bearer <token>"

# Health check (no token needed, any service)
curl http://localhost:8080/actuator/health

# Raw Prometheus metrics for a service
curl http://localhost:8081/actuator/prometheus
```

See **[API.md](API.md)** for the full endpoint reference — the Gateway also routes Level Tracker (`/api/level`) and Activity Level Threshold (`/api/threshold`) endpoints, not just Activity/Activity Log.

## Documentation

- **[API.md](API.md)** — all REST endpoints and their request/response shapes
- **[docs/features/](docs/features/)** — deep-dives into the notable engineering work (JWT/IDOR, rate limiting, event-driven decoupling, concurrency-safe XP, and more), each with a diagram and the load-bearing code
- **[postman/](postman/)** — a ready-to-import Postman collection covering every endpoint, including a dedicated IDOR-verification folder
- Per-service READMEs: [eureka-server](eureka-server/README.md) · [api-gateway](api-gateway/README.md) · [activity-service](activity-service/README.md) · [gamification-service](gamification-service/README.md)

## Contributing

Open issues are labelled by priority and type. New to the project? Start with a
[**good first issue**](https://github.com/prashant-singh-2001/gamified_tracker/contribute).

Workflow: pick an issue → branch → open a PR that references it (`Fixes #<n>`).
