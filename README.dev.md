# AChan - Local Development Guide

## Prerequisites
- JDK 21
- Docker & Docker Compose
- Gradle (wrapper included)

## Quick Start

### 1. Start Infrastructure
```bash
docker-compose up -d
```

This starts:
- PostgreSQL on `localhost:5432`
- Kafka on `localhost:9092`
- Zookeeper on `localhost:2181`
- Kafka UI on `localhost:8081` (optional monitoring)

### 2. Verify Services
```bash
# Check all services are healthy
docker-compose ps

# View logs
docker-compose logs -f postgres
docker-compose logs -f kafka
```

### 3. Run Application in Dev Mode
```bash
./gradlew quarkusDev
```

Application starts on `http://localhost:8080`

Dev UI available at `http://localhost:8080/q/dev/`

### 4. Test Endpoints

Create thread:
```bash
curl -X POST http://localhost:8080/api/v1/threads \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/article", "slug": "example"}'
```

Get thread:
```bash
curl http://localhost:8080/api/v1/threads/{threadId}
```

Create post:
```bash
curl -X POST http://localhost:8080/api/v1/threads/{threadId}/posts \
  -H "Content-Type: application/json" \
  -d '{"content": "This is a **markdown** post with >>1 reference"}'
```

Get posts:
```bash
curl "http://localhost:8080/api/v1/threads/{threadId}/posts?limit=50&offset=0"
```

## Monitoring

### Kafka UI
Browse to `http://localhost:8081` to view:
- Topics (including `url-crawl-requests`)
- Messages
- Consumer groups

### Database
Connect with any PostgreSQL client:
```
Host: localhost
Port: 5432
Database: achan
User: achan
Password: achan
```

### Health Checks
```bash
curl http://localhost:8080/q/health/live
curl http://localhost:8080/q/health/ready
```

## Stopping Services

```bash
# Stop infrastructure
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

## Troubleshooting

### Flyway migration fails
```bash
# Clean database and restart
docker-compose down -v
docker-compose up -d postgres
./gradlew quarkusDev
```

### Kafka connection issues
```bash
# Verify Kafka is ready
docker-compose logs kafka | grep "started (kafka.server.KafkaServer)"

# Restart Kafka
docker-compose restart kafka
```

### Port conflicts
If ports 5432, 9092, or 8080 are in use:
1. Stop conflicting services
2. Or modify `docker-compose.yml` port mappings

## Configuration Profiles

### Development Profile (Default)
When running `./gradlew quarkusDev`, the `dev` profile is automatically activated with these settings:
- OpenTelemetry disabled (to avoid warning logs about missing OTEL collector)
- Verbose logging for `concord.dev` package

**Note:** Kubernetes extensions (`quarkus-kubernetes` and `quarkus-kubernetes-config`) are commented out in `build.gradle` for local development. Uncomment them when preparing for Kubernetes deployment.

### Enabling OpenTelemetry
If you want distributed tracing in local development:

1. Add an OTEL collector to `docker-compose.yml`:
```yaml
  jaeger:
    image: jaegertracing/all-in-one:latest
    ports:
      - "16686:16686"  # Jaeger UI
      - "4317:4317"    # OTLP gRPC receiver
    environment:
      - COLLECTOR_OTLP_ENABLED=true
```

2. Start the collector:
```bash
docker-compose up -d jaeger
```

3. Remove or comment out the OTEL disable setting in `src/main/resources/application-dev.yml`:
```yaml
# quarkus:
#   otel:
#     sdk:
#       disabled: true
```

4. Restart the application and view traces at `http://localhost:16686`