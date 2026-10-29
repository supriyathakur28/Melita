# One image definition for every service: packages the jar that `./mvnw package` built and tested.
#   docker build --build-arg MODULE=order-intake-service .
FROM eclipse-temurin:21-jre

ARG MODULE
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY ${MODULE}/target/${MODULE}-*.jar app.jar

# Never run as root.
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
