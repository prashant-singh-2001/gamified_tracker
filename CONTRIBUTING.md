# Contributing to gamified_tracker

Thanks for your interest in contributing! This document covers how to
propose changes, coding conventions, and how to run things locally.

`gamified_tracker` is a Spring Boot / Spring Cloud microservices system
(`eureka-server`, `api-gateway`, `activity-service`, `gamification-service`) built around a three-phase roadmap — Observability, then
Resilience/rate-limiting, then Async decoupling. Please understand that before making
architectural changes, so new work stays consistent with the current phase.

## Before You Start

- Check open issues and [MAINTAINERS.md](./MAINTAINERS.md) to see if the
  area you want to touch already has context or an owner.
- For anything non-trivial (new service, new dependency, changes to shared
  modules), open an issue first to discuss the approach before writing code.
- Small fixes (typos, bug fixes, doc updates) can go straight to a PR.

## Branch Naming

Use a `<type>/<short-description>` format:

| Type | Use for |
|------|---------|
| `feature/` | New functionality |
| `fix/` | Bug fixes |
| `chore/` | Tooling, build, dependency bumps |
| `docs/` | Documentation-only changes |
| `refactor/` | Non-behavior-changing restructuring |

Examples: `feature/gamification-badge-tiers`, `fix/eureka-healthcheck-timeout`,
`chore/bump-spring-boot-3.5.14`.

## Commit Conventions

Squash commits before raising PR, i.e. there should be a single commit under a single PR to ensure clean commit history.

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short summary>

[optional body]

[optional footer, e.g. Closes #33]
```

- **type**: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`
- **scope**: the module/service affected, e.g. `activity-service`,
  `api-gateway`, `docker-compose`

Example:

```
feat(gamification-service): add leaderboard caching

Adds a Caffeine-backed cache in front of the leaderboard query to
reduce load on repeated reads.

Closes #28
```

## Running Tests Locally

Each module is tested independently with Maven:

```bash
# Run tests for a single module
cd activity-service
mvn test

# Run tests for all modules from the repo root
mvn test
```

If your change touches the shared parent POM (`gamified-tracker-parent`), run `mvn test` across **all** modules, not just the one you
edited — changes there ripple across services.

### Running the Full Stack Locally

The working pattern for this project is:

- **Infra services** (Postgres, Eureka, Zipkin, Prometheus, Grafana) run via
  Docker Compose:
  ```bash
  docker compose up -d
  ```
- **Application services** you're actively developing are run locally
  (e.g. `mvn spring-boot:run` or from your IDE), not in Docker, so you get
  fast reload cycles.
- If a locally-run service needs to reach something in Docker (e.g.
  Zipkin, Prometheus scraping), use `host.docker.internal` in the relevant
  config rather than a container name.
- The [AI weekly coaching digest](docs/features/ai-weekly-digest.md)'s two model backends are
  opt-in and neither starts with a plain `docker compose up -d` — see that doc's "Choosing a
  backend" section for the `--profile insights` (Ollama) and `-f docker-compose.insights-dmr.yml`
  (Docker Model Runner) commands.

## Pull Request Expectations

- **Keep PRs scoped** to a single concern where possible — easier to review,
  easier to revert.
- **Link related issues** (e.g. `Closes #22`).
- **Tests**: new behavior should come with test coverage; bug fixes should
  include a regression test where practical.
- **Docs**: if you're adding or changing a feature, add or update the
  corresponding doc under `docs/features/` (see below). If you're changing
  shared config (e.g. `application-observability.yml`, sampling
  probabilities, Grafana provisioning), call that out explicitly in the PR
  description since it affects all services.
- **CODEOWNERS** will auto-request the relevant reviewer based on the paths
  you've touched — no need to manually tag people unless you want extra
  eyes.
- **Pipeline**: GitHub Actions CI must pass before merge. See below for a manual workaround (whenever needed).

#### Pull Request Validation Workflow

This repository uses **GitHub Actions** to validate Pull Requests before they are merged into `main`.

1. Fork this repository.
2. Clone your fork and create a feature branch.

3. Implement your changes and push the branch to your fork.

4. Open a Pull Request from your feature branch to the `main` branch.

5. Navigate to the **Actions** tab of **your fork**.

6. Select the **PR Validation** workflow.

7. Click **Run workflow**.

8. In the branch dropdown, select the same branch used to create the PR (e.g. `feature/my-feature`).

9. Click **Run workflow**.

Once the workflow completes successfully, the PR is ready for review and merge (subject to the repository's branch protection rules).

## Feature Documentation Format

Feature-level documentation lives under `docs/features/*.md`. When adding a
new feature or materially changing an existing one, add or update a doc
there following the existing format in that directory — this keeps feature
behavior documented separately from the architectural notes in
`DESIGN_PATTERNS.md`.

## Code Style

- Standard Java/Spring Boot conventions; match the style already used in the
  module you're editing.
- Prefer environment-variable overrides over hardcoded values in shared
  config.
- Keep module Spring Boot versions in sync with the parent POM
  (`gamified-tracker-parent`) unless there's a documented reason to diverge.

## Questions?

Open an issue, or see [MAINTAINERS.md](./MAINTAINERS.md) for whom to reach
out to for a specific area.