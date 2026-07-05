FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl ca-certificates \
	&& rm -rf /var/lib/apt/lists/*

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src src
RUN ./mvnw -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=builder /workspace/target/quarkus-app/ /app/

ENV QUARKUS_HTTP_HOST=0.0.0.0
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
