FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 webhook
COPY --from=build /workspace/target/*.jar /app/webhook-service.jar
USER webhook
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/webhook-service.jar"]
