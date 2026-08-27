ARG TEMURIN_JDK_IMAGE=eclipse-temurin:25-jdk
ARG TEMURIN_JRE_IMAGE=eclipse-temurin:25-jre-jammy

FROM ${TEMURIN_JDK_IMAGE} AS builder

WORKDIR /workspace

RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl ca-certificates \
	&& rm -rf /var/lib/apt/lists/*

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src src
RUN ./mvnw -DskipTests package

FROM ${TEMURIN_JRE_IMAGE}

ARG APP_VERSION=0.9.0-SNAPSHOT
ARG VCS_REF=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="ChatBackend" \
	org.opencontainers.image.description="Private messaging platform backend" \
	org.opencontainers.image.version="${APP_VERSION}" \
	org.opencontainers.image.revision="${VCS_REF}" \
	org.opencontainers.image.created="${BUILD_DATE}" \
	org.opencontainers.image.source="https://github.com/pHantompX3/ChatBackend"

RUN groupadd --gid 10001 chatbackend \
	&& useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin chatbackend

WORKDIR /app
COPY --from=builder --chown=10001:10001 /workspace/target/quarkus-app/ /app/

ENV QUARKUS_HTTP_HOST=0.0.0.0
EXPOSE 8080

USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
