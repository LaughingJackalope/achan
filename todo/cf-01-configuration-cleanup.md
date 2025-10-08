# CF-01: Configuration Cleanup and Validation

## Status
**TODO** - Low priority

## Issues to Address

### 1. Deprecated Configuration Properties
Current warnings in logs:
```
WARN: The "quarkus.log.console.json" config property is deprecated
WARN: The "quarkus.hibernate-orm.database.generation" config property is deprecated
WARN: Unrecognized configuration key "quarkus.kafka.bootstrap-servers"
```

**Actions:**
- Replace `quarkus.log.console.json` with new logging configuration
- Remove `quarkus.hibernate-orm.database.generation` (we use Flyway anyway)
- Fix Kafka bootstrap servers configuration (likely should be under different namespace)

### 2. OpenTelemetry Endpoint
Current warning:
```
Failed to export TraceRequestMarshaler. Connection refused: localhost/127.0.0.1:4317
```

**Actions:**
- Either configure actual OTEL collector endpoint
- Or disable OpenTelemetry in dev mode
- Document in README.dev.md how to enable OTEL properly

### 3. Kubernetes Errors
```
ERROR: Cannot apply manifests because the Kubernetes dev service is not running
```

**Actions:**
- Consider removing quarkus-kubernetes extension if not needed for local dev
- Or configure it properly
- Or add to .gitignore: target/kubernetes/

## Files Involved
- `src/main/resources/application.yml`
- `build.gradle` (potentially remove unused extensions)

## Priority
**LOW** - These are warnings, not blocking issues