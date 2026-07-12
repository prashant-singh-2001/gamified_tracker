# Eureka Server

**Service discovery / registry for the Gamified Tracker platform.** · Port **8761**

Netflix Eureka server where every other service registers on startup and which they query to locate each other. Nothing in the system holds hard-coded host:port for a peer — the API Gateway and Activity Service resolve `activity-service` / `gamification-service` through this registry when making Feign calls.

## Role in the system

```
   ┌───────────────────────────────────────────────┐
   │            Eureka Server  (8761)               │
   │   registry of {service-name → instances}       │
   └───────────────────────────────────────────────┘
        ▲            ▲            ▲            ▲
        │ register + heartbeat / fetch registry
        │            │            │            │
   api-gateway   activity     gamification   (any future
    (8080)      service(8081)  service(8082)   service)
```

Every service is a Eureka **client**; this module is the only Eureka **server**. It does not register with itself and does not fetch a registry from anywhere (it *is* the source of truth).

## Responsibilities

- Maintain the live registry of service instances (name, host, port, status).
- Receive heartbeats from clients and evict instances that stop renewing their lease.
- Serve the registry to clients so Feign / Spring Cloud LoadBalancer can resolve service names.
- Expose the Eureka dashboard for humans.

## Tech stack

- Java 17, Spring Boot 3.5, Spring Cloud 2025 (`spring-cloud-starter-netflix-eureka-server`)
- Entry point: `EurekaServerApplication` — annotated `@SpringBootApplication` + `@EnableEurekaServer`
- No database, no Spring Security, no business REST endpoints.

## Dashboard & endpoints

This service exposes **no application REST API**. What it does serve:

| URL | Purpose |
|-----|---------|
| `http://localhost:8761/` | Eureka dashboard — lists registered applications and their instances/status |
| `http://localhost:8761/eureka/apps` | Raw registry (XML/JSON) consumed by clients — not meant for manual use |
| `http://localhost:8761/actuator/health` | Health check — `spring-boot-starter-actuator` is now a dependency, exposing `health` and `info` |

## Configuration

From [`src/main/resources/application.yaml`](src/main/resources/application.yaml):

| Key | Value | Meaning |
|-----|-------|---------|
| `server.port` | `8761` | Registry / dashboard port |
| `eureka.client.register-with-eureka` | `false` | The server does not register itself |
| `eureka.client.fetch-registry` | `false` | The server does not pull a registry from a peer |
| `eureka.instance.hostname` | `localhost` | Advertised hostname |
| `management.endpoints.web.exposure.include` | `health,info` | Actuator endpoints exposed over HTTP |
| `management.endpoint.health.probes.enabled` | `true` | Enables `/actuator/health/liveness` and `/actuator/health/readiness` probe groups |

Clients point at it via `eureka.client.service-url.defaultZone: http://eureka-server:8761/eureka` (see each client service's `application.yaml`).

> The current `application.yaml` sets `enable-self-preservation: true` nested under `management.server`, not `eureka.server` (the property Eureka's self-preservation actually lives under). This is likely a misplaced key rather than intentional — worth a fix — though its practical effect is muted since Eureka's own default for self-preservation is already `true`.

## Running

**Whole stack (recommended)** — from the repo root:

```bash
docker-compose up --build
```

Eureka starts first; other services `depends_on` it. Dashboard: <http://localhost:8761>.

**Standalone:**

```bash
cd eureka-server
mvn spring-boot:run
```

It has no external dependencies (no DB), so it can run on its own; the other services will fail to register until it is up.

## Testing

```bash
mvn test          # from eureka-server/
```

Contains the default `EurekaServerApplicationTests` context-load test.

## Troubleshooting

- **A service isn't showing on the dashboard** — check that client's `eureka.client.service-url.defaultZone` resolves (`eureka-server` hostname exists on the Docker network; use `localhost:8761` when running a client outside Docker), and that the client actually started.
- **Instances linger after a service dies** — self-preservation is on, so eviction is deliberately slow; expected in dev.
- **Startup ordering** — bring Eureka up before the clients, or clients log connection-refused until it's reachable (they retry). Under `docker-compose up`, this is enforced automatically: every client service's `depends_on` gates on Eureka reporting `service_healthy` via its Dockerfile `HEALTHCHECK` against `/actuator/health`.
- **Health check:** `curl http://localhost:8761/actuator/health`.

## Related docs

- [Root README](../README.md) — system overview & quick start
- [API.md](../API.md) — full REST reference for the other services
