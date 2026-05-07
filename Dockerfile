FROM maven:3.8.6-jdk-8 AS builder

WORKDIR /app

COPY repo/ ./

RUN mvn clean package -DskipTests

FROM eclipse-temurin:8-jre

WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser

COPY --from=builder /app/target/token-service-1.0.0.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
