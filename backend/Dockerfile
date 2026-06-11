# ---- Stage 1: build the Spring Boot jar from the multi-module reactor ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy poms first so dependency resolution is layer-cached across source-only changes.
COPY pom.xml ./
COPY platform/pom.xml platform/
COPY identity/pom.xml identity/
COPY patient-registry/pom.xml patient-registry/
COPY catalogue/pom.xml catalogue/
COPY visit-management/pom.xml visit-management/
COPY cashier/pom.xml cashier/
COPY doctor-appointment/pom.xml doctor-appointment/
COPY department-services/pom.xml department-services/
COPY clinical-case/pom.xml clinical-case/
COPY pharmacy/pom.xml pharmacy/
COPY bed-stay-forms/pom.xml bed-stay-forms/
COPY premature/pom.xml premature/
COPY emergency/pom.xml emergency/
COPY app/pom.xml app/
# Best-effort warm-up of the local repo; the real build below resolves anything missed.
RUN mvn -q -B -pl app -am dependency:go-offline || true

COPY . .
RUN mvn -q -B -pl app -am package -DskipTests

# ---- Stage 2: minimal JRE runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Hardened document storage root (mounted as a named volume in compose).
ENV HMS_ATTACHMENTS_DIR=/var/hms/attachments
RUN mkdir -p /var/hms/attachments

COPY --from=build /build/app/target/hms-app.jar /app/hms-app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/hms-app.jar"]
