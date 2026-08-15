# CF-01: Configuration Cleanup and Validation

## Status
**DONE** - Low priority (configuration warnings resolved)

### 1. Deprecated Configuration Properties
✅ **Resolved**: All three deprecated property warnings addressed:
- `quarkus.kafka.bootstrap-servers`: Moved from custom `kafka:` prefix to proper `quarkus.kafka` namespace in `src/main/resources/application.yml`
- `quarkus.hibernate-orm.database.generation`: Removed from test profile `src/test/resources/application-test.yml` (project uses Flyway for migrations)
- `quarkus.log.console.json`: Config uses new format `console.json.enabled: true` (not old deprecated syntax)

### 2. OpenTelemetry Endpoint
✅ **Resolved**: Dev profile `src/main/resources/application-dev.yml` already has `otel.sdk.disabled: true` to avoid connection refused warnings. Production setup documented in `README.dev.md` with Jaeger collector addition to `docker-compose.yml`.

### 3. Kubernetes Errors
✅ **Resolved**: `quarkus-kubernetes` and `quarkus-kubernetes-config` extensions are commented out in `build.gradle` for local development. Documented in `README.dev.md` - uncomment when preparing for Kubernetes deployment.

## Files Modified
- `src/main/resources/application.yml` - Kafka bootstrap servers moved to `quarkus.kafka` namespace; old `kafka:` block removed
- `src/test/resources/application-test.yml` - Deprecated `hibernate-orm.database.generation` removed