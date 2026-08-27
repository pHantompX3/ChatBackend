ARG TEMURIN_JDK_IMAGE=eclipse-temurin:25-jdk
ARG FLYWAY_BASE_IMAGE=flyway/flyway:13.3.0-alpine

FROM ${TEMURIN_JDK_IMAGE} AS driver

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw --batch-mode --no-transfer-progress dependency:copy \
      -Dartifact=com.microsoft.sqlserver:mssql-jdbc:13.2.1.jre11 \
      -DoutputDirectory=/driver

FROM ${FLYWAY_BASE_IMAGE}

USER root
RUN apk upgrade --no-cache \
    && find /flyway/drivers -mindepth 1 -maxdepth 1 -exec rm -rf -- '{}' + \
    && find /flyway/lib/flyway -maxdepth 1 -type f \( \
      -name 'flyway-database-*.jar' \
      -o -name 'flyway-firebird-*.jar' \
      -o -name 'flyway-gcp-*.jar' \
      -o -name 'flyway-locations-*.jar' \
      -o -name 'flyway-mysql-*.jar' \
      -o -name 'flyway-nc-*.jar' \
      -o -name 'flyway-singlestore-*.jar' \) -delete \
    && addgroup -g 10002 flyway-runtime \
    && adduser -D -H -u 10002 -G flyway-runtime flyway-runtime

COPY --from=driver --chown=10002:10002 \
  /driver/mssql-jdbc-13.2.1.jre11.jar /flyway/drivers/mssql-jdbc-13.2.1.jre11.jar

USER 10002:10002
