# Service Discovery, Health, Containerization & API Docs

**Services:** all four · **Key classes:** `EurekaServerApplication`, Actuator config,
`OpenApiConfig` (per service)

## What it is / why it's notable

A cluster of platform features that don't need much code to build correctly, but do need to be
wired together correctly — service discovery, health checks, container builds, startup
orchestration, and API docs. The interesting part is the **orchestration**: how service discovery,
health checks, and container startup ordering compose into a system that boots itself in the right
sequence without a human watching a terminal. A `docker-compose up` on this project brings up seven
containers (Postgres, RabbitMQ, Redis, Eureka, gateway, activity, gamification) in a *dependency
order enforced by real health checks*, not fixed sleep delays.

## Service discovery — Eureka

`eureka-server` is a bare `@EnableEurekaServer` application — no custom code beyond that annotation.
The interesting behavior is entirely on the client side: `api-gateway` registers itself and, via
[Java-DSL routes](api-gateway-routing.md), resolves `activity-service`/`gamification-service` by
**name** (`lb://activity-service`) rather than a hardcoded host:port. Client config tunes lease
timing tighter than the Eureka defaults for faster failure detection in a local/demo environment:
```yaml
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
    metadata-map:
      zone: ap-south-1
      version: v1
```
If a service instance goes down, Eureka removes it from the registry once its lease expires; when it
restarts, it re-registers automatically — no gateway restart or config reload needed.

## Health checks driving container startup order

Every service exposes Actuator `health`/`info` with liveness/readiness probe groups enabled:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
```
Each service's Dockerfile turns that into a real Docker-level healthcheck:
```dockerfile
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --spider --quiet http://localhost:8080/actuator/health || exit 1
```
`docker-compose.yml` then chains startup on those healthchecks, not fixed delays:
```
postgres, rabbitmq, redis  (independent)
        │
   eureka-server (waits on postgres healthy)
        │
     gateway (waits on postgres + eureka + redis + rabbitmq healthy)
        │
    activity (waits on postgres + eureka + gateway + rabbitmq healthy)
        │
  gamification (waits on postgres + eureka + activity + gateway + rabbitmq healthy)
```
On the gateway, `/actuator/**` is `permitAll`'d in both `SecurityConfig` and `JwtFilter` — health
checks (Docker's own, or an external monitor) never need a token.

## Containerization — layered, multi-stage, non-root

The healthcheck above lives in a Dockerfile that's built for fast rebuilds and a small, safe runtime
image — all four services share the same pattern:
```dockerfile
# build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline        # dependency layer cached until pom.xml changes
COPY src ./src
RUN mvn clean package -DskipTests
RUN java -Djarmode=layertools -jar target/*.jar extract   # split the fat jar into layers

# run stage
FROM eclipse-temurin:17-jre-alpine   # JRE, not JDK — smaller runtime
RUN addgroup -S MyGroup && adduser -S MyUser -G MyGroup
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./
USER MyUser                           # non-root
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```
Three deliberate choices worth calling out: **multi-stage** (the JDK + Maven never ship in the final
image — only a JRE does), **Spring Boot's `layertools` extraction** (dependencies, loader, and
application code become separate Docker layers, so a code-only change rebuilds just the small
application layer instead of re-shipping every dependency), and a **non-root user** for the runtime.

## API documentation — Swagger/OpenAPI per service

Each of the three application services runs its own springdoc-generated Swagger UI, independently
browsable:
```java
@Bean
public OpenAPI customOpenAPI() {
    return new OpenAPI().info(new Info()
            .title("API Gateway/Auth Service")
            .description("Central API Gateway - Authentication"));
}
```
```yaml
springdoc:
  swagger-ui: { path: /swagger-ui.html, enabled: true }
  api-docs: { path: /v3/api-docs }
```
Swagger paths are `permitAll`'d on the gateway alongside `/actuator/**`, so the docs load without
authentication — useful for anyone evaluating the API without first minting a token.

## Try it

```bash
curl http://localhost:8080/actuator/health          # no token needed
curl http://localhost:8761                            # Eureka dashboard — see all registered instances
open http://localhost:8080/swagger-ui.html             # gateway's own API surface
open http://localhost:8081/swagger-ui.html              # activity-service directly
open http://localhost:8082/swagger-ui.html               # gamification-service directly
docker-compose up --build                                 # watch the healthcheck-gated boot order
```

## Related
[API Gateway Routing](api-gateway-routing.md) (consumes Eureka for `lb://` resolution) ·
[Event-Driven Decoupling](event-driven-decoupling.md) (RabbitMQ is the other compose healthcheck
dependency) · [`API.md` § Health Checks](../../API.md#health-checks-actuator)
