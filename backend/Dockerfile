FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S pulseclinic && adduser -S pulseclinic -G pulseclinic \
    && apk add --no-cache wget \
    && mkdir -p /app/uploads \
    && chown -R pulseclinic:pulseclinic /app/uploads
COPY --from=build /app/target/pulseclinic-api-0.0.1-SNAPSHOT.jar app.jar
USER pulseclinic
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=12 CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
