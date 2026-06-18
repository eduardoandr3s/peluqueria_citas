# syntax=docker/dockerfile:1

# ── Stage 1: build (compila el jar ejecutable) ──────────────────────────────
# Imagen Maven con JDK 21: evita depender de mvnw (y de sus saltos de linea
# CRLF al venir de Windows). No se ejecutan los tests en la imagen (se corren
# en CI/local); el deploy solo empaqueta.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Capa de dependencias cacheable: solo se reconstruye si cambia el pom.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ── Stage 2: runtime (imagen ligera solo con el JRE) ────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# spring-boot-maven-plugin reempaqueta el jar ejecutable como
# peluqueria-<version>.jar (el original no ejecutable queda como .jar.original,
# que NO casa con *-SNAPSHOT.jar).
COPY --from=build /app/target/*-SNAPSHOT.jar app.jar

# Cloud Run enruta el trafico al puerto que indique la env var PORT (8080 por
# defecto). EXPOSE es meramente documental.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
