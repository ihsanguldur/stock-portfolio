FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml pom.xml
COPY common common
COPY auth-service auth-service
COPY wallet-service wallet-service
COPY stock-service stock-service
COPY portfolio-service portfolio-service
COPY transaction-service transaction-service
COPY audit-service audit-service
COPY api-gateway api-gateway
RUN mvn -q -pl api-gateway -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/api-gateway/target/api-gateway-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]