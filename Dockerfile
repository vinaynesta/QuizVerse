# Railway builds this image. Build stage compiles the jar with the Maven wrapper; the runtime stage only has a JRE.
# Tests run in CI (.github/workflows/maven.yml), so they're skipped here.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Railway sets PORT; application.properties reads it.
EXPOSE 8080
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
