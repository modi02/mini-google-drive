FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY target/storage-node-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /app/storage-data
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
