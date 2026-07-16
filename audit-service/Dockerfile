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
RUN mvn -q -pl common,audit-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/audit-service/target/audit-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]