FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline -DskipTests

COPY src/ src/
RUN ./mvnw -B -ntp package -DskipTests \
    && cp target/api-*.jar /application.jar

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN useradd --system --uid 10001 --no-create-home appuser
WORKDIR /app
COPY --from=build --chown=appuser:appuser /application.jar application.jar

USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
