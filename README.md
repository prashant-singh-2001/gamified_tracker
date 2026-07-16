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
```

- **API Gateway** — single entry point; handles JWT auth and routes to services.
- **Activity Service** — manages activities and logs activity sessions (computes XP).
- **Gamification Service** — tracks levels, thresholds, and awards points.
- **Eureka Server** — service registry/discovery.

## Tech stack

Java 17 · Spring Boot 3.5 · Spring Cloud 2025 (Eureka) · RabbitMQ (Spring AMQP) · Spring Security + JWT · PostgreSQL 15 · Maven · Docker Compose

## Quick start

**Prerequisites:** Docker + Docker Compose.

```bash
# 1. Configure environment (defaults are fine for local dev)
cp .env.example .env

# 2. Build and run everything
docker-compose up --build
```

That's it — all services, Eureka, and PostgreSQL start together.

## Ports

| Service              | Port |
|----------------------|------|
| API Gateway          | 8080 |
| Activity Service     | 8081 |
| Gamification Service | 8082 |
| Eureka dashboard     | 8761 |
| PostgreSQL           | 5433 |

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
```

See **[API.md](API.md)** for the full endpoint reference — the Gateway also routes Level Tracker (`/api/level`) and Activity Level Threshold (`/api/threshold`) endpoints, not just Activity/Activity Log.

## Documentation

- **[API.md](API.md)** — all REST endpoints and their request/response shapes
- **[postman/](postman/)** — a ready-to-import Postman collection covering every endpoint, including a dedicated IDOR-verification folder
- **[TODO-FIX.md](TODO-FIX.md)** — backlog of improvements and known gaps
- Per-service READMEs: [eureka-server](eureka-server/README.md) · [api-gateway](api-gateway/README.md) · [activity-service](activity-service/README.md) · [gamification-service](gamification-service/README.md)

## Contributing

Open issues are labelled by priority and type. New to the project? Start with a
[**good first issue**](https://github.com/prashant-singh-2001/gamified_tracker/contribute).

Workflow: pick an issue → branch → open a PR that references it (`Fixes #<n>`).
