FROM eclipse-temurin:21-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

WORKDIR /deployments

# The application will be mounted via volume in docker-compose
# This allows for quick rebuilds without rebuilding the Docker image

# Run the application
EXPOSE 8080

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

CMD ["sh", "-c", "java ${JAVA_OPTS} -jar /deployments/quarkus-run.jar"]
